package ai.dvine.ops.recon;

/** Simple editable stand-ins. One photograph does not reveal hidden surfaces. */
public final class ProceduralGeometryBuilder {
    public EditableScene motorcycle() {
        EditableScene s = new EditableScene();
        s.label = "Motorcycle";
        s.approximation = true;
        s.add(part("frame", "frame", "black", "frame | box | black | middle centre | large"));
        s.add(part("front_fork", "front fork", "grey", "front fork | box | grey | below frame | medium tall"));
        s.add(part("front_brake", "front brake", "silver", "front brake | box | silver | in front of front fork | tiny"));
        s.add(part("front_wheel", "front wheel", "black", "front wheel | cylinder | black | below front fork, sideways | medium"));
        s.add(part("front_tyre", "front tyre", "black", "front tyre | ring | black | around front wheel | medium"));
        s.add(part("front_rim", "front rim", "silver", "front rim | disc | silver | inside front wheel | small"));
        s.add(part("rear_wheel", "rear wheel", "black", "rear wheel | cylinder | black | below frame, sideways | medium"));
        s.add(part("engine", "engine", "grey", "engine | box | grey | inside frame | medium"));
        s.add(part("seat", "seat", "brown", "seat | box | brown | above frame | small"));
        s.add(part("handlebar", "handlebar", "black", "handlebar | box | black | above front fork | small wide"));
        s.add(part("tank", "tank", "red", "tank | box | red | above frame | medium"));
        return s;
    }

    public EditableScene car() {
        EditableScene s = new EditableScene();
        s.label = "Car";
        s.approximation = true;
        s.add(part("body", "body", "red", "body | box | red | middle centre | large wide"));
        s.add(part("roof", "roof", "red", "roof | box | red | above body | medium flat"));
        s.add(part("windows", "windows", "sky", "windows | box | sky | inside roof | medium"));
        s.add(part("front_left_tyre", "front-left tyre", "black", "front left tyre | cylinder | black | below body, sideways | small"));
        s.add(part("front_left_rim", "front-left rim", "silver", "front left rim | disc | silver | inside front left tyre | tiny"));
        s.add(part("door", "door", "red", "door | box | red | on body, left | medium"));
        return s;
    }

    public EditableScene chair() {
        EditableScene s = new EditableScene();
        s.label = "Chair";
        s.approximation = true;
        s.add(part("seat", "seat", "brown", "seat | box | brown | middle centre | medium wide"));
        s.add(part("backrest", "backrest", "brown", "backrest | box | brown | above seat | medium tall"));
        s.add(part("leg", "leg", "brown", "leg | box | brown | below seat | small tall"));
        return s;
    }

    public EditableScene forLabel(String label) {
        String n = label == null ? "" : label.toLowerCase();
        if (n.contains("motor") || n.contains("bike")) {
            return motorcycle();
        }
        if (n.contains("car") || n.contains("vehicle")) {
            return car();
        }
        if (n.contains("chair")) {
            return chair();
        }
        EditableScene s = new EditableScene();
        s.label = label == null || label.isEmpty() ? "Object" : label;
        s.approximation = true;
        s.add(part("body", "body", "grey", "body | box | grey | middle centre | large"));
        return s;
    }

    private static EditableScene.Node part(String id, String semantic, String material, String line) {
        return new EditableScene.Node(id, semantic, material, line);
    }
}
