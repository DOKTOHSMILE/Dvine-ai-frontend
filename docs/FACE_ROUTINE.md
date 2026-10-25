# Face routine

One head. Pure black. The other persona is not drawn beside them.

## Switch

`MatrixStageView.ensureFace` dissolves the current head (spring, about half a second) and assembles the one who is about to speak. At the swap the alpha is near zero, so two faces are never readable at once.

Triggers:

- A `SAY` or `THINK` line whose agent is the other persona.
- You say their name.
- Say "Obi switch" or "Ivie switch". Speech may hear those as "Ubi switch" and "Eva switch"; both are accepted.
- Ask a question and they switch on their own when the other one has a line.
- Swipe turns the microphone on or off. It does not change the persona.

## Motion

All visible channels go through `Spring1D` (head slower, jaw a little quicker, still damped).

Idle, both happy: breathe ±1.2%, tiny yaw, blink, a small glance, a brow lift, a smile pulse. Ivie's rest smile is higher and her eyes soften. Obi is calmer, not blank.

Speaking: jaw opens on a softened pulse, not a random jitter. Brows lift a little on the same pulse.

## v5.1 note

Ivie keeps the flowing low-poly hair from the approved asset. Obi is the angular flat-top. This overrides the older hairless line in v5.0.
