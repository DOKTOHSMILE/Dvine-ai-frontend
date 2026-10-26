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

    /** Small expression bias from the affect state: smile, brow, head tilt in degrees. */
    void setMood(float smile, float brow, float tiltDeg);
}
