rootProject.name = "websocket_with_vt"

include(":websocket")
project(":websocket").projectDir = file("./websocket")

include(":websocket_vt")
project(":websocket_vt").projectDir = file("./websocket_vt")
