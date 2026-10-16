# Decisions

The less obvious choices, and what they cost.

## Plain Views, no AndroidX or Compose

The target is TVs with weak GPUs and little memory. Framework Views are precompiled into the
system image, so Couch has nothing to warm up: no JIT-compiled UI toolkit, no first-scroll stutter.
The APK stays under 200 KB. The cost is more hand-written view code, kept manageable by small
classes (see [architecture](architecture.md)).

## Only render properties move

Focus changes animate scale, alpha and translation, which the render thread applies without
redrawing anything. Rows glide by translating a strip instead of scrolling, so holding the D-pad
retargets one smooth animation instead of restarting a scroll (the stock smooth scroll snaps when a
new scroll starts within 250 ms of the last). Text that changes on every focus move lives in
fixed-size views, so it never triggers a layout pass.

## Bitmaps are drawn at their final size

Banners, posters and backdrops are decoded, scaled and corner-rounded off the main thread at the
exact pixel size they are shown. Drawing them is then a plain copy. Backdrops are 720p RGB_565:
behind the scrim the softness doesn't show, and they move less than half the bytes of 1080p ARGB.

## HOME button

Some TVs hardwire HOME to the stock launcher. Couch replaces the stock launcher when its window
appears, through an accessibility service limited to that launcher's window events.

The alternative is to catch the HOME key itself (accessibility key filtering). It avoids the
stock launcher flashing and the few-second delay Android imposes on activity starts right after a
HOME press. It was rejected: filtering keys routes every remote press through the app, and on the
tested TV it stopped held keys from repeating, so holding volume no longer ramped.

## Configuration in a JSON file

Settings live in `config.json` in the app's external files directory rather than in private
preferences, so they can be read, backed up and edited over ADB without root, and custom icons and
wallpaper sit next to them.

## Network use

Each network feature is opt-in or replaceable: the wallpaper can be a local image or a gradient,
weather can be turned off or given a city instead of IP location. Reddit's own API refuses
anonymous clients, so subreddit wallpapers go through the public Arctic Shift archive.
