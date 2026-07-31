# database-internals

Contains prototypes for below:
1. ConnectionPool — [`ConnectionPool`](demo/src/main/java/com/example/database/internals/ConnectionPool/) (`ConnectionPoolDemo`, unit tests)
2. Sharding
2b. Consistent Hashing — [`ConsistentHashing`](demo/src/main/java/com/example/database/internals/ConsistentHashing/) (`ConsistentHashingDemo`, unit tests)
3. Pessimistic Locking — [`AirlineCheckin`](demo/src/main/java/com/example/database/internals/PessimisticLocking/) (`schema.sql`, then run with optional `--blocking`)
4. Reatime chat with socket.io
5. LSM trees
6. Bloom Filters — see [`demo/src/main/java/com/example/database/internals/BloomFilter/README.md`](demo/src/main/java/com/example/database/internals/BloomFilter/README.md) for interview talking points.
   Measure:
    a) False Positive Rate vs Size of the Filter
    b) False Positive Rate vs Number of Hash Functions
    c) Optimal sizing from expected insertions `n` and target FPP `p`
   Run: `cd demo && mvn test -Dtest=BloomFilterUnitTest`
7. Google File System

To run the real-time chat run the below commands
1. npm init -y
2. npm install express socket.io
3. To start server - npm start
4. To open client - double-click index.html file and open in browser