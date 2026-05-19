#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Automated review loop for the KT-30270 allDistinct use-case
# research document (Use-case-research.md).
# Codex reviews the document -> Claude validates & applies fixes.
# Usage: ./review-loop.sh [--force-regen] <from> <to(included)>
# ============================================================

# The document under review. The reviewer reads this file directly;
# Claude may edit ONLY this file. Paths are repo-relative; the script
# cd's to the repo root below.
DESIGN_DIR="libraries/stdlib/design/KT-30270-all-distinct"
SIBLING_DIR="libraries/stdlib/design/KT-10380-all-equal"
REVIEW_TARGET="${DESIGN_DIR}/Use-case-research.md"
REVIEW_DIR="${DESIGN_DIR}/review"

# Local checkout of the repositories surveyed by Use-case-research.md (one
# directory per repository), used to verify the document's file:line
# citations against the real code. Absolute path, outside the Kotlin repo.
CORPUS_DIR="/Users/dmitry.nekrasov/dev/repos/big-repos/kotlin-research-repos"

# Paths Claude is allowed to modify. Anything else is reverted.
# For a single-document review that is exactly the document itself.
ALLOWED_PATH_PREFIXES=(
    "${REVIEW_TARGET}"
)

COMMIT_MSG_FILE=""
REVIEW_TMP=""
cleanup() { rm -f "${COMMIT_MSG_FILE:-}" "${REVIEW_TMP:-}"; }
trap 'cleanup; echo " Interrupted."; exit 130' INT TERM
trap 'cleanup' EXIT

FORCE_REGEN=false
if [[ "${1:-}" == "--force-regen" ]]; then
    FORCE_REGEN=true
    shift
fi

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 [--force-regen] <from> <to(included)>"
    echo "  --force-regen: re-generate review files even if they already exist"
    echo "  from: first review iteration number"
    echo "  to:   last review iteration number (included)"
    exit 1
fi

START=$1
END=$2

if ! [[ "$START" =~ ^[0-9]+$ ]] || ! [[ "$END" =~ ^[0-9]+$ ]]; then
    echo "ERROR: from/to must be positive integers" >&2
    exit 1
fi

if [[ $START -gt $END ]]; then
    echo "ERROR: from ($START) must be <= to ($END)" >&2
    exit 1
fi

# Resolve repo root: this script lives at
# libraries/stdlib/design/KT-30270-all-distinct/review-loop.sh, i.e. 4 levels deep.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../../../.."

# Discover read-only context/reference docs: every *.md in this design folder
# except the review target, plus every *.md in the sibling KT-10380 folder.
# These are the documents Use-case-research.md cross-references. The review/
# subdir is not matched by the non-recursive *.md glob.
shopt -s nullglob
CONTEXT_DOCS=()
for doc in "${DESIGN_DIR}"/*.md "${SIBLING_DIR}"/*.md; do
    if [[ "$doc" != "$REVIEW_TARGET" ]]; then
        CONTEXT_DOCS+=("$doc")
    fi
done
shopt -u nullglob

# Pre-formatted bullet list of context docs, embedded into the prompts.
if [[ ${#CONTEXT_DOCS[@]} -gt 0 ]]; then
    CONTEXT_DOCS_LIST=$(printf '   - %s\n' "${CONTEXT_DOCS[@]}")
else
    CONTEXT_DOCS_LIST="   (none available)"
fi

# ---- Pre-flight checks ----
for cmd in codex claude git; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "ERROR: '$cmd' not found in PATH" >&2
        exit 1
    fi
done

if [[ ! -f "$REVIEW_TARGET" ]]; then
    echo "ERROR: Document under review not found: ${REVIEW_TARGET}" >&2
    exit 1
fi

if [[ ${#CONTEXT_DOCS[@]} -eq 0 ]]; then
    echo "WARNING: No context docs found near ${DESIGN_DIR} / ${SIBLING_DIR};" >&2
    echo "         the reviewer will run without cross-reference material." >&2
fi

if [[ ! -d "$CORPUS_DIR" ]]; then
    echo "WARNING: Surveyed-corpus directory not found: ${CORPUS_DIR}" >&2
    echo "         The reviewer cannot verify file:line citations against the" >&2
    echo "         real code; it will fall back to consistency checks only." >&2
fi

if [[ -n $(git status --porcelain) ]]; then
    echo "ERROR: Working tree has uncommitted changes. Commit or stash first." >&2
    git status --short >&2
    exit 1
fi

# A valid review must contain the "## Status" section
review_is_valid() { [[ -f "$1" ]] && grep -q '## Status' "$1"; }

# Status is "ready" only if the '## Status' section contains the magic phrase
review_status_is_ready() {
    [[ -f "$1" ]] && awk '
      /^## Status/ { in_s = 1; next }
      in_s && /^## / { exit }
      in_s && /Ready for merge/ { found = 1 }
      END { exit !found }
    ' "$1"
}

# Returns 0 (true) if the path is OUTSIDE every allowed prefix.
is_stray_path() {
    local path="$1"
    local prefix
    for prefix in "${ALLOWED_PATH_PREFIXES[@]}"; do
        if [[ "$path" == "$prefix"* ]]; then
            return 1
        fi
    done
    return 0
}

mkdir -p "$REVIEW_DIR"

# Live log files for `tail -f`; copied to per-iteration files after each run
CODEX_LIVE_LOG="${REVIEW_DIR}/log-codex.txt"
CLAUDE_LIVE_LOG="${REVIEW_DIR}/log-claude.txt"

echo "=== Review loop: KT-30270, iterations ${START}..${END} ==="
echo "=== Branch: $(git branch --show-current) ==="
echo "=== Document under review: ${REVIEW_TARGET} ==="
echo "=== Surveyed corpus: ${CORPUS_DIR} ==="
echo "=== Context docs (${#CONTEXT_DOCS[@]} files): ==="
if [[ ${#CONTEXT_DOCS[@]} -gt 0 ]]; then
    printf '  %s\n' "${CONTEXT_DOCS[@]}"
else
    echo "  (none)"
fi
echo "=== Allowed path prefixes: ==="
printf '  %s\n' "${ALLOWED_PATH_PREFIXES[@]}"
echo ""

COMMITS_MADE=0
CONSECUTIVE_NO_CHANGES=0

print_iter_footer() {
    local nn=$1 start_epoch=$2
    local end_epoch elapsed
    end_epoch=$(date +%s)
    elapsed=$((end_epoch - start_epoch))
    echo "=== Iteration ${nn} finished at $(date '+%H:%M:%S') (elapsed: $((elapsed / 60))m $((elapsed % 60))s) ==="
    echo ""
}

for i in $(seq "$START" "$END"); do
    NN=$(printf '%02d' "$i")
    REVIEW_FILE="${REVIEW_DIR}/codex-review-${NN}.md"
    CODEX_LOG="${REVIEW_DIR}/log-${NN}-codex.txt"
    CLAUDE_LOG="${REVIEW_DIR}/log-${NN}-claude.txt"
    COMMIT_MSG_FILE="${REVIEW_DIR}/.commit-msg-${NN}.tmp"

    rm -f "$COMMIT_MSG_FILE"
    ITER_START_EPOCH=$(date +%s)

    echo "=== Iteration ${NN} started at $(date '+%H:%M:%S') ==="

    HEAD_BEFORE=$(git rev-parse HEAD)

    # ---- Phase 1: Codex writes review ----

    if [[ "$FORCE_REGEN" == true ]] && [[ -f "$REVIEW_FILE" ]]; then
        echo "[${NN}] --force-regen: removing existing review file."
        rm -f "$REVIEW_FILE"
    fi

    if review_is_valid "$REVIEW_FILE"; then
        echo "[${NN}] Review file already exists and is valid, skipping Codex phase."
    else
        if [[ -f "$REVIEW_FILE" ]]; then
            echo "[${NN}] WARNING: Review file exists but appears incomplete, regenerating."
            rm -f "$REVIEW_FILE"
        fi
        echo "[${NN}] Phase 1: Codex review..."

        # Collect previous reviews for context (current iteration's file is
        # already removed above if --force-regen, so it never appears here).
        shopt -s nullglob
        PREV_REVIEW_FILES=("${REVIEW_DIR}"/codex-review-*.md)
        shopt -u nullglob
        if [[ ${#PREV_REVIEW_FILES[@]} -gt 0 ]]; then
            PREV_REVIEWS=$(printf '   - %s\n' "${PREV_REVIEW_FILES[@]}")
        else
            PREV_REVIEWS="   (none — this is the first iteration)"
        fi

        # Codex writes to a temp file; we atomically move it on success
        REVIEW_TMP="${REVIEW_FILE}.tmp"
        rm -f "$REVIEW_TMP"

        CODEX_PROMPT="You are reviewing a use-case research document for the Kotlin
standard library: KT-30270 (the allDistinct / allDistinctBy API family).

Read these inputs:

1. The document under review — this is the ONLY artifact under review.
   Read it in full:
       ${REVIEW_TARGET}

2. Context / reference documents. The document under review
   cross-references these; use them to verify its claims, cross-references,
   and links. Treat them as authoritative — if the document disagrees with
   them, that is a finding against the document:
${CONTEXT_DOCS_LIST}
   You may also read any other file in the repository, and may consult the
   cited YouTrack issues — use the YouTrack MCP server if one is configured,
   otherwise the public REST API, e.g.
       https://youtrack.jetbrains.com/api/issues/KT-85976?fields=summary,description

3. Previous reviews — DO NOT repeat comments already addressed in the
   current state of the document:
${PREV_REVIEWS}

Write your review to: ${REVIEW_TMP}

Required structure:
   # Review of KT-30270 allDistinct use-case research (iteration ${NN})
   ## Summary
   ## Status
   ## Comments
   ## Conclusion

For each comment include:
- Priority: High / Medium / Low
- Title
- Locations (section and/or line references within the document)
- Description: what is inaccurate, internally inconsistent, unsupported,
  unclear, or inconsistent with the context documents
- Concrete suggestion to fix
- Sources: document sections, context-doc sections, official Kotlin docs,
  related YouTrack issues

What to look for:
- Internal numeric consistency: the document restates its key counts in
  several places — the headline total, the per-section and per-family
  subtotals, the section 3 findings table, the corpus size, and the
  comparison against the allEqualWith survey. Confirm they all agree
  wherever restated, that each total matches its breakdown, and that the
  section 2 corpus list length matches its stated repository count.
- Consistency with the context documents: every '../KT-10380-all-equal/...'
  cross-reference must resolve to a real file and, where an #anchor is
  used, a real heading; the document's characterization of the allEqualWith
  survey, the floating-point semantics decision, and the KT-30270 /
  KT-85976 API spec must match those source documents and Context.md.
- Consistency with the cited YouTrack issues (KT-30270, KT-85976, KT-10380,
  KT-78499): claims about what each issue proposes or states must be
  accurate.
- Logical soundness: the section 5 verdict and the claim-vs-evidence table
  must follow from the section 3 / section 4 findings; flag overclaiming
  and any caveat that is raised but not carried through.
- Citations: every repository named in sections 3 and 4 must appear in the
  section 2 corpus list; the same source must not be cited with conflicting
  line numbers; flag malformed or implausible file:line references. The
  surveyed repositories are checked out at ${CORPUS_DIR} (one directory
  per repository) — open the cited files there and confirm each file:line
  reference points at the code the document describes, tolerating small
  line-number drift from the document's snapshot. If that directory is
  absent, check citations only for internal consistency and plausibility.
- Clarity and completeness: flag genuinely confusing passages; the
  methodology must be reproducible; every idiom family in the section 3
  table must be discussed in the prose; the scope limits (the boolean-only
  subset, the out-of-scope variants) must be stated honestly.
- Markdown well-formedness: tables, fenced code blocks with language tags,
  link syntax, and correctly percent-encoded URLs for filenames that
  contain parentheses.

What NOT to flag:
- Commit message style, commit organization, or how the work is split
  across commits. The branch will be squashed into a single commit before
  it lands.
- Comments already addressed in the current document state, even if they
  appear in earlier reviews.
- Pure stylistic rephrasing that changes neither correctness, internal
  consistency, nor clarity.

Verification methods (pick what fits the claim):
- Internal consistency: cross-read every section that restates a number
  and confirm they agree; count the section 2 corpus list explicitly.
- Context-doc / link consistency: open the referenced file, locate the
  cited section, and compare.
- Citation accuracy: open the cited file under ${CORPUS_DIR} and confirm
  the file:line points at the code the document describes.
- YouTrack claims: consult the cited issue (KT-XXXXX) and compare.

Status — pick exactly one:
- \"Needs revision\" — at least one High comment OR the document contains a
  factual or internal-consistency error.
- \"Ready with minor remarks\" — only Medium/Low comments; the document is
  publishable. List the remaining remarks explicitly.
- \"Ready for merge\" — no further substantive comments.

Language: English."

        PHASE1_START=$(date +%s)
        echo "=== Run started at $(date '+%Y-%m-%d %H:%M:%S') (iteration ${NN}) ===" > "$CODEX_LIVE_LOG"
        # Note: --full-auto grants Codex unrestricted shell access
        if ! codex exec --full-auto --ephemeral "$CODEX_PROMPT" \
                >> "$CODEX_LIVE_LOG" 2>&1; then
            cp -- "$CODEX_LIVE_LOG" "$CODEX_LOG"
            echo "[${NN}] ERROR: Codex crashed (see ${CODEX_LOG}). Aborting." >&2
            exit 1
        fi
        cp -- "$CODEX_LIVE_LOG" "$CODEX_LOG"
        PHASE1_ELAPSED=$(( $(date +%s) - PHASE1_START ))
        echo "[${NN}] Phase 1 done ($((PHASE1_ELAPSED / 60))m $((PHASE1_ELAPSED % 60))s)"

        # Atomic move: only promote temp file if it looks valid
        if review_is_valid "$REVIEW_TMP"; then
            mv -f "$REVIEW_TMP" "$REVIEW_FILE"
        else
            echo "[${NN}] WARNING: Review temp file missing or incomplete (no '## Status' section)."
            rm -f "$REVIEW_TMP"
        fi
    fi

    # ---- Phase 2: Verify review file ----
    if [[ ! -f "$REVIEW_FILE" ]]; then
        echo "[${NN}] Phase 2: ERROR — review file not created. Skipping Claude phase."
        print_iter_footer "$NN" "$ITER_START_EPOCH"
        continue
    fi
    echo "[${NN}] Phase 2: Review verified, $(wc -l < "$REVIEW_FILE") lines"

    if review_status_is_ready "$REVIEW_FILE"; then
        echo "[${NN}] Phase 2: Review status READY. Stopping loop."
        print_iter_footer "$NN" "$ITER_START_EPOCH"
        break
    fi

    # ---- Phase 3: Claude validates and fixes ----
    echo "[${NN}] Phase 3: Claude validates and fixes..."

    CLAUDE_PROMPT="You are an editor for a use-case research document in the Kotlin
standard library: ${REVIEW_TARGET} (KT-30270, the allDistinct API family).

Inputs:
- The review to act on: ${REVIEW_FILE}
- The document under review — the ONLY file you may edit:
      ${REVIEW_TARGET}
- Context / reference documents (authoritative; read-only):
${CONTEXT_DOCS_LIST}
  You may also read any other file in the repository, and may consult the
  cited YouTrack issues — use the YouTrack MCP server if one is configured,
  otherwise the public REST API, e.g.
      https://youtrack.jetbrains.com/api/issues/KT-85976?fields=summary,description
- The repositories surveyed by the document are checked out at
  ${CORPUS_DIR} (one directory per repository); use them to verify the
  document's file:line citations against the real code. If that directory
  is absent, fall back to internal-consistency checks for citations.

For each comment in the review:
1. Verify the issue still exists in the current state of ${REVIEW_TARGET} —
   earlier iterations may have already fixed it. Verify with the method
   that matches the comment:
   - Internal consistency: re-read every section that restates the number
     and confirm whether they actually disagree.
   - Context-doc / link consistency: open the referenced file and the
     cited section and compare.
   - Citation accuracy: open the cited file under ${CORPUS_DIR} and
     confirm the file:line points at the described code.
   - YouTrack claims: consult the issue and compare.
2. If the issue is real, fix it by editing ${REVIEW_TARGET}.
   - Scope of edits: fix factual or numeric errors, internal-consistency
     mismatches, broken or incorrect cross-references and links, logical
     gaps or overclaiming, and genuinely confusing passages. Do NOT
     rewrite wording that is already correct and clear for style alone.
   - If a comment would require changing a context document, do NOT change
     it: the context documents are authoritative. Instead fix
     ${REVIEW_TARGET} so that it agrees with them.
   - Keep edits minimal and targeted; preserve the document's structure,
     voice, and Markdown style.
3. If the issue is invalid (already fixed, or the reviewer was mistaken),
   skip it.

Constraints:
- You may modify ONLY this file:
      ${REVIEW_TARGET}
  Any other file you change is reverted by the script.
- The script owns staging and committing. Do NOT run git add, git commit,
  git push, git reset, git rebase, git merge, or anything that touches the
  index, HEAD, or remotes.
- You MAY use read-only git commands (git diff, git status, git log,
  git show) freely.

After all fixes:
- If you edited the document, write a single-line description of the main
  fixes (English, under 200 chars) to:
      ${COMMIT_MSG_FILE}
  Format: apply review ${NN} — <description>
  Do NOT add any prefix — the script will prepend \"KT-30270 review: \".
- If no fix was needed, do NOT create the commit message file.

Ultrathink."

    PHASE3_START=$(date +%s)
    echo "=== Run started at $(date '+%Y-%m-%d %H:%M:%S') (iteration ${NN}) ===" > "$CLAUDE_LIVE_LOG"
    # Note: --dangerously-skip-permissions grants Claude unrestricted shell access
    if ! claude -p \
            --dangerously-skip-permissions \
            --no-session-persistence \
            --output-format stream-json \
            --verbose \
            "$CLAUDE_PROMPT" \
            >> "$CLAUDE_LIVE_LOG" 2>&1; then
        cp -- "$CLAUDE_LIVE_LOG" "$CLAUDE_LOG"
        echo "[${NN}] ERROR: Claude crashed (see ${CLAUDE_LOG}). Aborting." >&2
        exit 1
    fi
    cp -- "$CLAUDE_LIVE_LOG" "$CLAUDE_LOG"
    PHASE3_ELAPSED=$(( $(date +%s) - PHASE3_START ))
    echo "[${NN}] Phase 3 done ($((PHASE3_ELAPSED / 60))m $((PHASE3_ELAPSED % 60))s)"

    # ---- Phase 4: Commit if changes were made ----

    # Safety net: check if Claude committed despite instructions
    HEAD_AFTER=$(git rev-parse HEAD)
    if [[ "$HEAD_BEFORE" != "$HEAD_AFTER" ]]; then
        echo "[${NN}] WARNING: Claude made a commit despite instructions. Accepting it."
        echo "[${NN}] Claude committed: $(git log --oneline -1)"
        COMMITS_MADE=$((COMMITS_MADE + 1))
        CONSECUTIVE_NO_CHANGES=0
        rm -f "$COMMIT_MSG_FILE"
        print_iter_footer "$NN" "$ITER_START_EPOCH"
        continue
    fi

    # Revert tracked changes outside allowed prefixes
    STRAY_FILES=()
    while IFS= read -r f; do
        [[ -n "$f" ]] || continue
        if is_stray_path "$f"; then
            STRAY_FILES+=("$f")
        fi
    done < <(git diff HEAD --name-only)
    if [[ ${#STRAY_FILES[@]} -gt 0 ]]; then
        echo "[${NN}] WARNING: Claude modified files outside allowed prefixes, reverting:"
        printf '  %s\n' "${STRAY_FILES[@]}"
        echo "=== Stray file diff (iteration ${NN}, reverted) ===" >> "$CLAUDE_LOG"
        git diff HEAD -- "${STRAY_FILES[@]}" >> "$CLAUDE_LOG"
        git checkout HEAD -- "${STRAY_FILES[@]}"
    fi

    # Remove untracked files Claude may have created outside allowed prefixes
    UNTRACKED_STRAY=()
    while IFS= read -r f; do
        [[ -n "$f" ]] || continue
        if is_stray_path "$f"; then
            UNTRACKED_STRAY+=("$f")
        fi
    done < <(git ls-files --others --exclude-standard)
    if [[ ${#UNTRACKED_STRAY[@]} -gt 0 ]]; then
        echo "[${NN}] WARNING: Claude created untracked files outside allowed prefixes, removing:"
        printf '  %s\n' "${UNTRACKED_STRAY[@]}"
        rm -f -- "${UNTRACKED_STRAY[@]}"
    fi

    # Stage everything under the allowed prefixes; then check if anything was staged.
    git add -- "${ALLOWED_PATH_PREFIXES[@]}"

    if ! git diff --cached --quiet; then
        if [[ -f "$COMMIT_MSG_FILE" ]] && [[ -s "$COMMIT_MSG_FILE" ]]; then
            COMMIT_DESC=$(head -n 1 -- "$COMMIT_MSG_FILE" | cut -c1-200)
            COMMIT_MSG="KT-30270 review: ${COMMIT_DESC}"
        else
            COMMIT_MSG="KT-30270 review: apply review ${NN} fixes"
        fi

        echo "$COMMIT_MSG" | git commit -F -
        echo "[${NN}] Committed: $(git log --oneline -1)"
        COMMITS_MADE=$((COMMITS_MADE + 1))
        CONSECUTIVE_NO_CHANGES=0
    else
        echo "[${NN}] No changes from this iteration."
        CONSECUTIVE_NO_CHANGES=$((CONSECUTIVE_NO_CHANGES + 1))
        if [[ $CONSECUTIVE_NO_CHANGES -ge 3 ]]; then
            echo "=== ${CONSECUTIVE_NO_CHANGES} consecutive no-change iterations. Stopping. ==="
            rm -f "$COMMIT_MSG_FILE"
            print_iter_footer "$NN" "$ITER_START_EPOCH"
            break
        fi
    fi

    rm -f "$COMMIT_MSG_FILE"
    print_iter_footer "$NN" "$ITER_START_EPOCH"
done

echo "=== Done. Commits: ${COMMITS_MADE}, Consecutive no-change: ${CONSECUTIVE_NO_CHANGES} ==="
