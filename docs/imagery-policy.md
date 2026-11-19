# Imagery policy

Status: v1, approved by Henry on 19 November 2026. Applies to every AI-generated or curated image in the app.
Images are illustrations that help people engage with Scripture. They are never presented as historical records,
and they never contain text.

## 1. Depicting God and Jesus

- **God and Jesus are shown in human form, but as light:** a glowing, luminous human silhouette with **no
  detailed face**. Features are never rendered; the figure is defined by light, not by facial likeness.
- This applies to every scene where Jesus or God appears (for example the Baptism, the Transfiguration, the
  Resurrection appearances, the burning bush, the Sermon on the Mount). Brief fields: `divine_figure: light_silhouette`.
- **Faceless hands are also allowed where the context calls for them** (Henry, 19 November 2026): for example God's hand
  reaching from the clouds at the creation. Only the hand (and perhaps a sleeve or cloud) is shown, never a face or a
  full figure. An editor chooses this per story; the brief field is `divine_figure: faceless_hand`.
- The Holy Spirit is shown only through the imagery Scripture itself uses (a dove at the Baptism, tongues of fire
  at Pentecost), never as a person.
- The infant Jesus in Nativity scenes follows the same rule: the child is surrounded by and partly formed of
  light, with no detailed face.

## 2. Other figures

- People are depicted with **historically plausible ancient Near Eastern and Mediterranean appearance, dress and
  setting**. Avoid European-default depictions.
- Angels: luminous, reverent, not fantasy creatures; no halos unless the "iconographic" style is chosen.
- Violence (Crucifixion, battles, judgements): reverent and not graphic. Show consequence and grief, not gore.
- No nudity (Eden scenes use foliage, distance and composition).

## 3. Accuracy

- Follow the priority order: 1 direct depiction, 2 historical context, 3 symbolic, 4 atmospheric.
- Do not invent events, people or details the text does not state. Where details are unknown or disputed
  (for example the number of Magi), keep them vague rather than presenting one interpretation as fact.
- No modern objects, no text or lettering of any kind in the image.
- Symbolic images are labelled as symbolic in "About this image".

## 4. Generate once, share with everyone

- An image is generated **once** and the approved image is **used for every user**. Nothing is ever generated
  on a user's device or per user, and nothing is generated when someone opens a passage.
- Images belong to **scenes** (canonical verse ranges), not to cards or translations, so one image serves every
  verse density and every future translation.
- Approved files are stored **content-addressed** (`img/<sha256>.<width>.avif`), so identical files are stored
  and downloaded once, and devices that already have a file never download it again.
- Before generating, the pipeline checks for an existing approved image for the same scene, and for a previous
  generation from the same brief (same brief hash): it reuses rather than regenerates.
- Regeneration happens only when an editor rejects or replaces an image in the admin platform.

## 5. Review

- Every image passes automatic checks (anachronisms, text in image, the rules above) and **human approval**
  before it is published. Long-press → "About this image" shows that it is an AI-generated illustration,
  its depiction type, and that it was reviewed.
- Users can choose **"Landscapes and settings only"** to hide images that show people or divine figures.
