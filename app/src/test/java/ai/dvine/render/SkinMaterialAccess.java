package ai.dvine.render;

/** Test access to the skin feature shapes. */
public final class SkinMaterialAccess {
    public static float brow(float x, float y, float z, boolean female) {
        return SkinMaterial.brow(x, y, z, female);
    }

    public static float hair(float x, float y, float z, boolean female) {
        return SkinMaterial.hair(x, y, z, female);
    }
}
