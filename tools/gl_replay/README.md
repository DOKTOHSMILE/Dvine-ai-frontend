# GL replay

Shows what the phone's renderer draws, without a phone.

1. Record: `GLCAP_OUT=/tmp/cap/obi GLCAP_WHO=obi gradle :app:testDebugUnitTest --tests ai.dvine.render.GlCapture --rerun`
   (`GLCAP_E=1` for the full-body view, `GLCAP_YAW` to turn, `GLCAP_FRAMES` to run longer).
2. Serve the folder holding `play.html` and the capture: copy `play.html` next to the capture folder and run `python3 -m http.server 8765` there.
3. Replay and screenshot: `node tools/gl_replay/shot.js http://127.0.0.1:8765/play.html obi out.png`

The replay runs every recorded call in WebGL2 (ANGLE + SwiftShader) and draws only the last frame; it prints any GL error.
`tools/check_shaders.js` is the quick check that the shaders compile.
