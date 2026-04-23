from flask import Flask, render_template, request, jsonify
from flask_socketio import SocketIO, emit
from py4j.java_gateway import GatewayParameters, JavaGateway
from pwn import process
import json
import os

from near_live import DEFAULT_COUNTS, NearLiveController


status = "off"
reset = 0
reset_boolean = False
detection_mode = os.getenv("DETECTION_MODE", "near_live")
near_live = None
model_server = None
app_get = None


def empty_counts():
    return dict(DEFAULT_COUNTS)


def create_gateway_entrypoint():
    gateway_host = os.getenv("PY4J_GATEWAY_HOST", "cicflowmetter")
    gateway_port = int(os.getenv("PY4J_GATEWAY_PORT", "25333"))
    gateway = JavaGateway(
        gateway_parameters=GatewayParameters(address=gateway_host, port=gateway_port)
    )
    return gateway.entry_point


def create_app(test_config=None):
    global app_get
    global model_server
    global req
    global data
    global config
    global status
    global near_live

    data = empty_counts()
    req = False
    near_live = NearLiveController()

    if detection_mode == "legacy":
        model_server = process("./server.py")
        app_get = create_gateway_entrypoint()
    else:
        app_get = None
        model_server = None
        state = near_live.read_state()
        status = state.get("status", "off")
        data.update(near_live.read_result())

    app = Flask(__name__, instance_relative_config=True)
    socketio = SocketIO(app, logger=True)

    with open("config.json", "r", encoding="utf-8") as config_file:
        config = json.load(config_file)

    def sync_runtime_state():
        global status
        if detection_mode == "near_live":
            status = near_live.read_state().get("status", "off")
            data.update(near_live.read_result())

    @app.route("/")
    def index():
        global status
        sync_runtime_state()
        if config["auto-start"] == 1 and detection_mode == "near_live":
            near_live.write_state("on")
            status = "on"
        elif config["auto-start"] == 1 and detection_mode == "legacy" and app_get is not None:
            status = "on"
            print("Starting IDS")
            app_get.startTrafficFlow()
        payload = {
            "status": status,
            "auto_start": config["auto-start"],
            "level_threat": config["level-threat"],
            "reset_level": config["reset-level"],
            "detection_mode": detection_mode,
        }
        return render_template("index.html", **payload)

    @app.route("/start", methods=["POST"])
    def start():
        global status
        status = "on"
        print(f"Starting IDS in {detection_mode} mode")
        if detection_mode == "legacy" and app_get is not None:
            app_get.startTrafficFlow()
        else:
            near_live.write_state("on")
        return "0"

    @app.route("/stop", methods=["POST"])
    def stop():
        global status
        status = "off"
        print(f"Stopping IDS in {detection_mode} mode")
        if detection_mode == "legacy" and app_get is not None:
            app_get.stopTrafficFlow()
        else:
            near_live.write_state("off")
        return "0"

    @app.route("/info/<attack>")
    def info(attack):
        return render_template(f"{attack}.html")

    @app.route("/reset_traffic", methods=["POST"])
    def reset_traffic():
        global data
        global reset
        global reset_boolean
        reset += 1
        reset_boolean = True
        print("Data reset!")
        data = empty_counts()
        if detection_mode == "near_live":
            near_live.write_result(data)
        return "0"

    @app.route("/update_settings", methods=["GET", "POST"])
    def update_settings():
        global config
        payload = request.get_json()
        with open("config.json", "w", encoding="utf-8") as config_file:
            json.dump(payload, config_file)
        config = payload
        return jsonify(status="success")

    @app.route("/post-predict", methods=["POST"])
    def postpredict():
        global data
        received = request.get_json()
        print("Data received!")
        for key in data:
            data[key] = int(received.get(key, 0))
        if detection_mode == "near_live":
            near_live.write_result(data)
        print(data)
        return "1"

    @app.route("/runtime-status", methods=["GET"])
    def runtime_status():
        sync_runtime_state()
        return jsonify(status=status, result=data, mode=detection_mode)

    @app.route("/reset_status", methods=["GET", "POST"])
    def reset_status():
        global reset_boolean
        if request.method == "POST":
            reset_boolean = False
            print("Status reset ", reset_traffic)
            return "1"
        return jsonify(reset_boolean=str(reset_boolean))

    @socketio.on("connect")
    def test_connect():
        print("client connected")

    @socketio.on("disconnect")
    def test_disconnect():
        print("Client disconnected")

    @socketio.on("request_predection")
    def request_handle():
        global req
        req = True
        while req:
            try:
                sync_runtime_state()
                emit("predection", {"result": data})
                socketio.sleep(3)
            except Exception:
                pass

    @socketio.on("stop_predection")
    def stopping():
        global req
        req = False
        print(req)

    return [socketio, app]


if __name__ == "__main__":
    socketio, app = create_app()
    socketio.run(app, host="0.0.0.0", port=7777, allow_unsafe_werkzeug=True)
