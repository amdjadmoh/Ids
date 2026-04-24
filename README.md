# Anomaly Detection System

This project was tested in `offline` mode.

The idea is simple:

1. convert a `.pcap` file into a CICFlowMeter CSV
2. copy the CSV into the model folder
3. start the Docker model service
4. run prediction on the CSV

## Requirements

- Docker + Docker Compose
- Java 8
- `sudo` access

## Offline run

From the project root:

### 1. Generate the CSV from the PCAP

```bash
cd CICFlowMeter-master
sudo sh run_offline_pcap.sh ../tmp/strong-test4.pcap ./data/offline
```

This creates:

```text
CICFlowMeter-master/data/offline/strong-test4.pcap_Flow.csv
```

### 2. Copy the CSV into the model examples folder

```bash
cp ./data/offline/strong-test4.pcap_Flow.csv ../model/data_examples/
```

### 3. Build and start the model container

Go back to the project root:

```bash
cd ..
docker compose build model
docker compose up -d model
```

### 4. Run prediction

```bash
docker compose exec model sh -lc 'cd /app && python offline_predict.py data_examples/strong-test4.pcap_Flow.csv'
```

## Full command list

If your friend wants the exact commands in one place:

```bash
cd CICFlowMeter-master
sudo sh run_offline_pcap.sh ../tmp/strong-test4.pcap ./data/offline
cp ./data/offline/strong-test4.pcap_Flow.csv ../model/data_examples/

cd ..
docker compose build model
docker compose up -d model
docker compose exec model sh -lc 'cd /app && python offline_predict.py data_examples/strong-test4.pcap_Flow.csv'
```

## Notes

- Live mode is not working yet.
- This README documents the offline flow that was actually tested.
- If `run_offline_pcap.sh` fails, check that Java 8 is installed.
