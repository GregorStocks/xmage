# Architecture

## Philosophy: Stock Server, Minimal Client Changes

Don't touch Java outside of `Mage.Client.Streaming` and `Mage.Client.Headless`. All AI harness functionality should be implemented in:

- `Mage.Client.Streaming` - streaming/observer client (subclasses Mage.Client)
- `Mage.Client.Headless` - headless client for AI harness
- `puppeteer/` - Python orchestration layer

### Why?

- **Easier to stay in sync with upstream** - We can pull from xmage/master without merge conflicts
- **Cleaner separation** - The server is "dumb" infrastructure; intelligence lives in our client modules
- **Simpler deployment** - Can use stock XMage server releases
