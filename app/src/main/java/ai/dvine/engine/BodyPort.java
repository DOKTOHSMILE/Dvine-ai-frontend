package ai.dvine.engine;

import ai.dvine.face.Sketch;

/** What the engine can make the on-screen body do. The stage implements it. */
public interface BodyPort {
    void setBodyMode(boolean on);

    void setPose(String pose);

    void playScene(String[] poses, float[] seconds);

    void turnAround();

    void redraw();

    void gesture(String key);

    void showSketch(Sketch sketch);

    void showObject(String kind);

    void clearSketch();

    /** A facial expression for a few seconds: smile, frown, surprise, attention, sad, angry. */
    void setExpression(String name);

    /** Both personas side by side (the demo scene), or back to one. */
    void setDemo(boolean on);

    /** Arranges both personas in the shared scene: single, side (side by side), meet (facing each other), walk (walk together). */
    void arrange(String arrangement);

    /** Small expression bias from the affect state: smile, brow, head tilt in degrees. */
    void setMood(float smile, float brow, float tiltDeg);
}
