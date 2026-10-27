package ai.dvine.engine;

import ai.dvine.stage.Sketch;

/**
 * What the engine can make the stage do: show drawings and built-in 3D forms, and move a body the user had DVINE
 * design (the movement engine: poses, moves, scenes, a turn). The stage implements it. There is no face.
 */
public interface BodyPort {
    /** The designed body moves into a pose or a looping move ("walk", "dance", "pushups"…). */
    void setPose(String pose);

    /** The designed body acts out a scene: one pose after another, each for its seconds. */
    void playScene(String[] poses, float[] seconds);

    /** The thing on show turns all the way round. */
    void turnAround();

    void showSketch(Sketch sketch);

    void showObject(String kind);

    void clearSketch();

    /** The mood from the affect state (smile, brow, tilt); the pod's light may follow it. */
    void setMood(float smile, float brow, float tiltDeg);
}
