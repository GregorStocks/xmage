## Git

Local `master` is often behind. Always use `origin/master` as the source of truth for rebasing and diffing:

```bash
git fetch origin
git rebase origin/master
```

## Code Isolation Philosophy

Avoid **modifying existing behavior** in Java outside of `Mage.Client.Streaming` and `Mage.Client.Headless`. This means not changing existing methods, fields, or logic in `Mage.Client`, `Mage.Server*`, `Mage.Common`, `Mage`, `Mage.Sets`, etc. Changing existing behavior makes incorporating upstream XMage updates difficult.

**Additive changes are OK:** Adding new methods, fields, or classes to upstream modules is fine as long as existing behavior is untouched — these merge cleanly.

**Our code (free to modify):**
- `Mage.Client.Streaming` - streaming/observer client
- `Mage.Client.Headless` - headless client for AI harness
- `puppeteer/` - Python orchestration

## Python

Always use `uv` for Python. Never use system Python directly.

```bash
# Run a Python script
uv run python script.py

# Run a module
uv run --project puppeteer python -m puppeteer
```

## Running the AI Harness

Use Makefile targets instead of running uv commands directly:

```bash
# No-LLM game: 1 sleepwalker + 1 potato + 2 CPU players (no API keys needed)
make run-dumb

# 1 LLM pilot + CPU opponents (needs OPENROUTER_API_KEY)
make run-llm

# 4 LLM pilots battle each other (needs OPENROUTER_API_KEY)
make run-llm4

# Record to specific file
make run-dumb OUTPUT=/path/to/video.mov

# Pass additional args
make run-dumb ARGS="--config myconfig.json"
```

Recordings are saved to `.context/ai-harness-logs/recording_<timestamp>.mov` by default.

## Issues

Issues are tracked as JSON files in `issues/`. See `doc/issues.md` for format and queries.
