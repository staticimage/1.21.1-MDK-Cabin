#!/usr/bin/env bash
# Live two-player Transient Diviner test (see diviner_mp.gradle / DivinerMultiplayerSmoke.java).
#     bash tools/visual_smoke/diviner_mp.sh
# Starts an offline dedicated server on port 25599 plus two offline clients (Alice, Bob) on this machine, runs the
# scripted scenario, and prints the server's star/viewer log and verdict. Screenshots: build/mp/{alice,bob}/screenshots.
set -u
cd "$(dirname "$0")/../.."
INIT=tools/visual_smoke/diviner_mp.gradle
rm -rf build/mp/server/world build/mp/alice/screenshots build/mp/bob/screenshots
mkdir -p build/mp/server
echo "eula=true" > build/mp/server/eula.txt
printf "online-mode=false\nserver-port=25599\nspawn-protection=0\nallow-flight=true\nmotd=Diviner MP test\nview-distance=10\nsimulation-distance=8\nlevel-seed=cabin\nenforce-secure-profile=false\n" > build/mp/server/server.properties

./gradlew -I $INIT runServer -PmpRole=server --no-configuration-cache > build/mp/server.log 2>&1 &
SERVER=$!
for _ in $(seq 1 300); do
    grep -q "DIVINER_MP server ready" build/mp/server.log 2>/dev/null && break
    kill -0 $SERVER 2>/dev/null || { echo "server failed to start:"; tail -20 build/mp/server.log; exit 1; }
    sleep 1
done
./gradlew -I $INIT runClientOffline -PmpRole=alice --no-configuration-cache > build/mp/alice.log 2>&1 &
ALICE=$!
sleep 5
./gradlew -I $INIT runClientOffline -PmpRole=bob --no-configuration-cache > build/mp/bob.log 2>&1 &
BOB=$!
wait $ALICE $BOB
for _ in $(seq 1 60); do kill -0 $SERVER 2>/dev/null || break; sleep 1; done
kill $SERVER 2>/dev/null

grep -h "DIVINER_MP" build/mp/server.log | grep -E "t=|CHECK|RESULT"
grep -h -E "DIVINER_MP .*(heard|done)" build/mp/alice.log build/mp/bob.log
echo "screenshots: build/mp/alice/screenshots, build/mp/bob/screenshots"
