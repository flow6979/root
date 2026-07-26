# Push Root to GitHub

## First push

```bash
cd /Users/vaibhavdixit/projects/lifestyle-app

git init
git add .
git commit -m "Root: initial vertical slice (Compose + time-adaptive theme + AI reflection)"

# Option A - GitHub CLI (creates the repo for you)
gh repo create root-app --private --source=. --remote=origin --push

# Option B - manual: create an empty repo on github.com first, then:
git branch -M main
git remote add origin git@github.com:<you>/root-app.git
git push -u origin main
```

## What is and isn't committed

- Committed: all source, `docs/`, `design/`, Gradle config, the wrapper.
- NOT committed (see `.gitignore`): `local.properties` (holds your SDK path and
  `GROQ_API_KEY`), `/build/`, keystores, `.idea/`. Secrets never go to GitHub.

## How a future / separate Claude session catches up

Point a fresh session at the cloned repo and say:
"Read CLAUDE.md and everything in docs/, then let's continue."
`CLAUDE.md` auto-loads; `docs/DECISIONS.md` carries the reasoning; the code carries
the state. That is the portable memory (local machine memory does NOT travel via git).

## Day-to-day

```bash
git checkout -b feat/<short-name>
# ...work...
git add -A && git commit -m "feat: ..."
git push -u origin HEAD
gh pr create --fill        # open a PR
```
