---
layout: home

hero:
  name: Couch
  text: A light, fast Android TV launcher
  tagline: Built for TVs whose chips struggle with the stock launcher. Plain Android Views, no ads, no tracking, and an APK under 200 KB.
  image:
    src: /logo.svg
    alt: Couch logo
  actions:
    - theme: brand
      text: Install Couch
      link: /setup
    - theme: alt
      text: Download the APK
      link: https://github.com/samuelloranger/couch/releases/latest

features:
  - title: Smooth on weak hardware
    details: Focus only animates render properties, rows glide instead of scrolling, and every image is decoded at the size it is drawn. Frames stay under 16 ms on a 2020 TV chip.
  - title: Nothing you didn't ask for
    details: No ads, no sponsored rows, no analytics. The network is only used for the features you turn on.
  - title: Yours to arrange
    details: Reorder, rename and hide apps, inputs and rows, pick each input's icon, and change the accent colour, all from the remote.
  - title: Takes over HOME
    details: Works on TVs that hardwire the HOME button to the stock launcher, and repairs itself when Android turns it off.
---

<div class="vp-doc" style="max-width: 1152px; margin: 48px auto 0;">

## What it looks like

<div class="shots">
  <figure>
    <img src="/screenshots/home.jpg" alt="Home screen with the apps row and the hero area" loading="lazy">
    <figcaption>Home: the focused show fills the hero area.</figcaption>
  </figure>
  <figure>
    <img src="/screenshots/channel.jpg" alt="An app channel row with its backdrop" loading="lazy">
    <figcaption>Preview channels from the apps you use.</figcaption>
  </figure>
  <figure>
    <img src="/screenshots/settings.jpg" alt="The Personnaliser settings screen" loading="lazy">
    <figcaption>Personnaliser: every option, from the remote.</figcaption>
  </figure>
</div>

## Features

- Hero area showing the focused show or film: backdrop, title, episode, time left, synopsis.
- Apps row, HDMI inputs row with brand logos (PlayStation, Xbox, Nintendo Switch, Apple TV…),
  and the preview channels apps publish.
- Wallpaper: Bing photo of the day, the best images of a subreddit, a gradient, or your own.
- Weather (Open-Meteo, located automatically or by city), clock, now playing.
- Ambient mode after a few idle minutes: full-screen wallpaper, large clock, weather.
- Long-press any card to move, rename, hide or change its icon.

The interface is in French. Start with [Setup](./setup).

</div>
