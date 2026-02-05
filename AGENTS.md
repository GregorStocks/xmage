## Code Isolation Philosophy

Don't touch Java outside of `Mage.Client.Streaming` and `Mage.Client.Headless`. This means avoiding changes to `Mage.Client`, `Mage.Server*`, `Mage.Common`, `Mage`, `Mage.Sets`, etc. This keeps us in sync with upstream XMage.

**Our code:**
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
# Start streaming observer (compiles first)
make ai-harness

# Start with video recording
make ai-harness-record

# Record to specific file
make ai-harness-record-to OUTPUT=/path/to/video.mov

# Skip compilation (faster iteration)
make ai-harness-skip-compile

# Record with skip compilation
make ai-harness-record-skip-compile
```

Recordings are saved to `.context/ai-harness-logs/recording_<timestamp>.mov` by default.
