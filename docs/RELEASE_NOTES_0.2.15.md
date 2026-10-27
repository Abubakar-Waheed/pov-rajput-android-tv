# POV Rajput 0.2.15

Four more debrid services, a Home that fits Continue Watching on the first screen, and WeTrakr lists that look like the rest of Flix.

## Debrid Services

- New: **Real-Debrid**, **AllDebrid**, **Premiumize** and **Debrid-Link**. Connect each with your own API key in **Settings → Debrid Services** (renamed from Streaming services) and rank them in **Debrid priority**.
- **Premiumize** reports cached links up front, like Offcloud and TorBox.
- **Real-Debrid, AllDebrid and Debrid-Link** no longer offer an up-front cache check, so their results say **Check on play**. When you play, the first 7 links are tried in order and the first cached one plays, with progress shown as it goes. Links that are not cached are removed from your account again.
- If none of the first 7 are cached and Offcloud or TorBox is connected, you can download the link you picked there.
- The player shows each service's logo; the TorBox logo is now sharp.

## Home

- With something in progress, the feature trims to a two-line description, so the first screen shows the feature, Continue Watching and the next row in full. Top 10 follows the first regular row.
- **Resume cards** (on by default): Continue Watching as wide cards with the artwork, title, episode, time left and progress. Turn them off in **Settings → Home & appearance** for regular cards with "S1 E3 · 42m left" captions.

## WeTrakr

- Liked lists on Home are ordinary Flix rows, with **See all** for the whole list; the first card no longer cuts off.
- The WeTrakr page uses the Home layout: Continue Watching, Up Next, watchlists, your lists and liked lists as rows.
