# Face routine

One head on pure black. The other persona is not drawn beside them.

## Geometry (procedural)

`face/WireHead.java` generates each head from numbers, not from hand-typed vertex tables:

- stacked super-ellipse rings from crown to chin, then neck rings; width and depth come from profile curves
- a feature field pushes brow, nose, cheekbones, lips and chin forward on the front of the face
- small deterministic jitter gives the organic low-poly triangulation of the reference art
- separate wire loops for eyes (three nested almonds each), brows, nose, lips and ears
- Ivie only: flowing hair strands from a centre parting, cross-linked into triangles, tips sway

`face/WirePainter.java` deforms (jaw, smile, brows, blink, gaze, breathing, hair sway), projects, buckets edges by depth and draws a glow pass and a line pass. Ivie adds glowing vertex dots.

Change a shape by editing the profile tables or `feature()` in `WireHead`; `docs/brand/face_preview.png` shows the current result.

## Switch

`MatrixStageView.ensureFace` dissolves the current head and assembles the next one. Triggers: a line from the other persona, or "Obi switch" / "Ivie switch" (also heard as "Ubi switch", "Eva switch").

## Motion

All channels go through `Spring1D` via `FaceRig`: blink, glance, brow lifts, smile pulses, jaw on speech, head tilt while listening, slow sway while thinking.

## Decision note

Ivie has flowing hair (v5.1 note and the latest reference art). Obi is hairless. This overrides v5.0's hairless line for Ivie.
