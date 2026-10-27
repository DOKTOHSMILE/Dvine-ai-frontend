# Character looks

`source/` holds the character cut-outs (transparent backgrounds) the project lead made for Obi and Ivie.
`atlas.py` turns them into the look pictures the app ships (`app/src/main/assets/looks/<who>_atlas.webp`): each view
cropped to the figure, scaled to one height, packed side by side, and the chest and back logo painted out.

    python3 tools/sheets/atlas.py ivie app/src/main/assets/looks front=tools/sheets/source/ivie_front.webp \
        side=tools/sheets/source/ivie_side.webp back=tools/sheets/source/ivie_back.webp face=tools/sheets/source/ivie_face.webp
    python3 tools/sheets/atlas.py obi app/src/main/assets/looks front=tools/sheets/source/obi_views.webp:0,400 \
        side=tools/sheets/source/obi_views.webp:400,630 back=tools/sheets/source/obi_views.webp:630,975 face=tools/sheets/source/obi_face.webp

`app/src/main/assets/looks/<who>.json` gives each view's box on the picture and where its landmarks are (crown, eyes,
nose, mouth, chin, ears, shoulders, elbows, wrists, fingertips, hips, knees, ankles, toes; `_l` is the person's own
left). If the pictures are rebuilt with a different layout, update the boxes from `<who>_atlas_boxes.json` and
re-check the landmarks. At start-up the app reads the outline of every view from the picture's transparency, shapes
the body to it (`human/wrap/SheetFit`), then paints the body from the views (`human/wrap/SheetWrap`).

Needs Python 3 with Pillow, NumPy and OpenCV (`pip install pillow numpy opencv-python-headless`).
