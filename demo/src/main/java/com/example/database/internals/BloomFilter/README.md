# Bloom Filters (interview talking points)

Probabilistic set membership used heavily in database storage engines (especially LSM trees).

## Guarantees

| Outcome | Possible? |
|---------|-----------|
| False negative (`mightContain` = false when key was inserted) | **Never** |
| False positive (`mightContain` = true when key was never inserted) | **Yes** |

Naming `mightContain` (not `contains`) signals that “true” only means *probably present*.

## Why databases use them

In LSM-based stores (LevelDB, RocksDB, Cassandra, etc.):

1. Data lives in immutable sorted files (SSTables / SST files).
2. A point lookup may need to check many files.
3. Each file keeps a small Bloom filter of its keys.
4. If the filter says **definitely not present**, skip the file → fewer disk reads.

Tradeoff: a few bits per key of RAM/metadata in exchange for fewer I/Os.

## Sizing formulas (know these cold)

Given expected insertions \(n\) and target false-positive rate \(p\):

\[
m = -\frac{n \ln p}{(\ln 2)^2}, \quad k = \frac{m}{n}\ln 2
\]

- \(m\) = bit array size  
- \(k\) = number of hash functions  
- Rule of thumb: ~10 bits/key ≈ 1% FPP with optimal \(k\)

This demo exposes both:

- `new BloomFilter(n, p)` — computes optimal \(m\) and \(k\)
- `new BloomFilter(m, k, n)` — manual knobs for experiments

## Hashing approach

Uses **double hashing** (Kirsch–Mitzenmacher):

\[
h_i(x) = h_1(x) + i \cdot h_2(x) \pmod m
\]

Two independent mixes produce \(k\) indices without running \(k\) full hash functions. Production systems often use Murmur3 / xxHash / CityHash; this demo uses a compact Murmur-style 64-bit mix.

Also: map hashes with `Math.floorMod` (not `Math.abs(hash % m)`) so `Integer.MIN_VALUE` cannot produce a negative bit index.

## What Bloom filters do *not* do

- **No deletes** in the classic structure (need Counting Bloom / other variants).
- **Not a replacement for an index** — they only filter candidates.
- **Not thread-safe** when mutating a shared `BitSet` (build offline, then treat as immutable is common).

## Variants worth mentioning

- **Counting Bloom** — support deletes (counters instead of bits)
- **Blocked / cache-line Bloom** — better CPU cache behavior (used in modern RocksDB)
- **Ribbon filters / Cuckoo filters** — alternative probabilistic filters with different space/FPP tradeoffs

## How to run

From `demo/`:

```bash
# JUnit guarantees + FPP sanity checks
mvn test -Dtest=BloomFilterUnitTest

# Manual FPP experiments (size / k / optimal sizing)
mvn -q exec:java -Dexec.mainClass=BloomFilterTest
```

If `exec-maven-plugin` is not configured, compile and run with:

```bash
mvn -q test-compile
java -cp target/classes BloomFilterTest
```

## Interview checklist

1. State the false-negative / false-positive guarantee first.  
2. Tie it to LSM SST lookups and disk I/O.  
3. Write the \(m\) / \(k\) formulas and explain the bits-per-key tradeoff.  
4. Mention double hashing and why `abs(hash % m)` is unsafe.  
5. Call out no-delete and concurrency limitations, then name one variant.
