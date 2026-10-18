#include "Search.h"

#include <algorithm>
#include <chrono>
#include <thread>
#include <unordered_map>
#include <unordered_set>

namespace geode {

namespace {

struct Cluster {
    int32_t geode_count = 0;
    int64_t center_x = 0;
    int64_t center_z = 0;
};

uint64_t pack_chunk(int64_t x, int64_t z) {
    return ((uint64_t)(uint32_t)x << 32) | (uint32_t)z;
};

struct RunningGuard {
    SearchControl* control;
    explicit RunningGuard(SearchControl* control) : control(control) {
        if (control != nullptr) {
            control->running.store(true, std::memory_order_relaxed);
        }
    }
    ~RunningGuard() {
        if (control != nullptr) {
            control->running.store(false, std::memory_order_relaxed);
        }
    }
};

bool wait_or_stop(SearchControl* control) {
    if (control == nullptr) {
        return false;
    }
    if (control->stop_requested.load(std::memory_order_relaxed)) {
        return true;
    }
    if (!control->pause_requested.load(std::memory_order_relaxed)) {
        return false;
    }
    control->status.store(1, std::memory_order_relaxed);
    while (control->pause_requested.load(std::memory_order_relaxed)
           && !control->stop_requested.load(std::memory_order_relaxed)) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    if (!control->stop_requested.load(std::memory_order_relaxed)) {
        control->status.store(0, std::memory_order_relaxed);
    }
    return control->stop_requested.load(std::memory_order_relaxed);
}

std::vector<Cluster> search_tile(const GeodeGen& prototype, int64_t start_x, int64_t end_x, int64_t start_z,
                                 int64_t end_z, int loaded_radius, int geode_threshold, SearchControl* control,
                                 std::vector<uint64_t>* geode_chunks) {
    std::vector<Cluster> clusters;
    if (start_x >= end_x || start_z >= end_z) {
        return clusters;
    }
    GeodeGen geode = prototype;
    int loaded_diameter = loaded_radius * 2 + 1;
    size_t tile_width = (size_t)(end_x - start_x);
    std::vector<uint8_t> chunk_history(tile_width * (size_t)loaded_diameter, 0);
    std::vector<int16_t> column_history(tile_width + (size_t)loaded_diameter, 0);
    std::vector<int16_t> column_delta(tile_width, 0);
    size_t chunk_history_index = 0;
    size_t chunk_history_reset = chunk_history.size();
    clusters.reserve(256);

    for (int64_t z = start_z; z < end_z; ++z) {
        if (wait_or_stop(control)) {
            clusters.clear();
            return clusters;
        }
        int64_t center_z = z - loaded_radius;
        int64_t scaled_z = geode.scale_z(z);
        uint8_t* slice = chunk_history.data() + chunk_history_index;
        for (size_t i = 0; i < tile_width; ++i) {
            int64_t x = start_x + (int64_t)i;
            bool is_geode = geode.check_fast(x, scaled_z);
            if (is_geode && geode_chunks != nullptr) {
                geode_chunks->push_back(pack_chunk(x, z));
            }
            column_history[i + (size_t)loaded_diameter] =
                (int16_t)(column_history[i + (size_t)loaded_diameter] + (int16_t)is_geode - (int16_t)slice[i]);
            slice[i] = is_geode ? 1 : 0;
        }
        for (size_t i = 0; i < tile_width; ++i) {
            column_delta[i] = (int16_t)(column_history[i + (size_t)loaded_diameter] - column_history[i]);
        }
        int32_t geode_count = 0;
        for (size_t i = 0; i < tile_width; ++i) {
            geode_count += column_delta[i];
            if (geode_count >= geode_threshold) {
                clusters.push_back(Cluster{geode_count, start_x + (int64_t)i - loaded_radius, center_z});
            }
        }
        chunk_history_index += tile_width;
        if (chunk_history_index == chunk_history_reset) {
            chunk_history_index = 0;
        }
        if (control != nullptr) {
            control->current.fetch_add(1, std::memory_order_relaxed);
        }
    }
    return clusters;
}

std::vector<Hit> process_clusters(const std::vector<Cluster>& clusters, const GeodeGen& prototype,
                                  int loaded_radius, int budding_threshold, bool want_budding,
                                  const std::unordered_set<uint64_t>* geode_mask, int64_t scan_min_x, int64_t scan_max_x,
                                  int64_t scan_min_z, int64_t scan_max_z, SearchControl* control) {
    std::vector<Hit> hits;
    GeodeGen worker = prototype;
    std::unordered_map<uint64_t, uint32_t> cache;
    cache.reserve((size_t)std::max(1, loaded_radius * loaded_radius * 2));

    for (const Cluster& cluster : clusters) {
        if (wait_or_stop(control)) {
            break;
        }
        int64_t min_x = cluster.center_x - loaded_radius;
        int64_t max_x = cluster.center_x + loaded_radius;
        int64_t min_z = cluster.center_z - loaded_radius;
        int64_t max_z = cluster.center_z + loaded_radius;
        uint32_t budding_count = 0;
        if (want_budding) {
            for (int64_t x = min_x; x <= max_x; ++x) {
                for (int64_t z = min_z; z <= max_z; ++z) {
                    bool inside = x >= scan_min_x && x < scan_max_x && z >= scan_min_z && z < scan_max_z;
                    uint64_t key = pack_chunk(x, z);
                    bool is_geode;
                    if (geode_mask != nullptr && inside) {
                        is_geode = geode_mask->find(key) != geode_mask->end();
                    } else {
                        is_geode = worker.check(x, z);
                    }
                    if (!is_geode) {
                        continue;
                    }
                    auto found = cache.find(key);
                    if (found == cache.end()) {
                        int generated = worker.generate(x, z);
                        uint32_t value = generated < 0 ? 0u : (uint32_t)generated;
                        found = cache.emplace(key, value).first;
                    }
                    budding_count += found->second;
                }
            }
        }
        bool keep = !want_budding || budding_count >= (uint32_t)budding_threshold;
        if (keep) {
            Hit hit;
            hit.block_x = (int32_t)(cluster.center_x * 16);
            hit.block_z = (int32_t)(cluster.center_z * 16);
            hit.budding = want_budding ? (int32_t)budding_count : -1;
            hit.geode_count = cluster.geode_count;
            hits.push_back(hit);
            if (control != nullptr && want_budding) {
                std::lock_guard<std::mutex> lock(control->result_mutex);
                control->partial.push_back(hit);
            }
        }
        if (control != nullptr) {
            control->current.fetch_add(1, std::memory_order_relaxed);
        }
    }
    return hits;
}

} // namespace

SearchControl& search_control() {
    static SearchControl control;
    return control;
}

void reset_search_control() {
    SearchControl& control = search_control();
    control.phase.store(0);
    control.current.store(0);
    control.total.store(0);
    control.status.store(0);
    control.pause_requested.store(false);
    control.stop_requested.store(false);
    control.running.store(false);
    std::lock_guard<std::mutex> lock(control.result_mutex);
    control.partial.clear();
}

std::vector<Hit> run_search(const SearchRequest& request, SearchControl* control) {
    if (request.min_chunk_x > request.max_chunk_x || request.min_chunk_z > request.max_chunk_z) {
        return {};
    }
    int loaded_radius = std::clamp(request.loaded_radius, 0, 64);
    int geode_threshold = std::max(1, request.geode_threshold);
    int budding_threshold = std::max(1, request.budding_threshold);
    int threads = request.threads <= 0 ? (int)std::thread::hardware_concurrency() : request.threads;
    if (threads < 1) {
        threads = 1;
    }

    int64_t start_x = request.min_chunk_x;
    int64_t end_x = (int64_t)request.max_chunk_x + 1;
    int64_t start_z = request.min_chunk_z;
    int64_t end_z = (int64_t)request.max_chunk_z + 1;
    int64_t span_x = end_x - start_x;
    int64_t span_z = end_z - start_z;
    if (span_x <= 0 || span_z <= 0) {
        return {};
    }
    if (control != nullptr) {
        reset_search_control();
    }
    [[maybe_unused]] RunningGuard running_guard(control);

    GeodeGen prototype = GeodeGen::create(request.version, request.seed);
    int64_t chunk_size = (span_x + threads - 1) / threads;

    if (control != nullptr) {
        control->phase.store(1);
        control->current.store(0);
        control->status.store(0);
    }

    struct Tile {
        int64_t start_x;
        int64_t end_x;
    };
    std::vector<Tile> tiles;
    for (int i = 0; i < threads; ++i) {
        int64_t tile_start = start_x + (int64_t)i * chunk_size;
        int64_t tile_end = tile_start + chunk_size;
        if (tile_start >= end_x) {
            continue;
        }
        int64_t lo = std::max(start_x, tile_start - loaded_radius);
        int64_t hi = std::min(end_x, tile_end + loaded_radius);
        tiles.push_back(Tile{lo, hi});
    }
    if (control != nullptr) {
        control->total.store(span_z * (long long)std::max<size_t>(1, tiles.size()));
    }

    std::vector<std::vector<Cluster>> partial(tiles.size());
    bool record_geodes = request.mode != SearchMode::StructureOnly;
    std::vector<std::vector<uint64_t>> geode_parts(record_geodes ? tiles.size() : 0);
    std::vector<std::thread> workers;
    workers.reserve(tiles.size());
    std::atomic<bool> phase1_stopped{false};
    for (size_t i = 0; i < tiles.size(); ++i) {
        workers.emplace_back([&, i]() {
            partial[i] = search_tile(prototype, tiles[i].start_x, tiles[i].end_x, start_z, end_z, loaded_radius,
                                      geode_threshold, control, record_geodes ? &geode_parts[i] : nullptr);
            if (control != nullptr && control->stop_requested.load(std::memory_order_relaxed)) {
                phase1_stopped.store(true);
            }
        });
    }
    for (std::thread& worker : workers) {
        worker.join();
    }
    if (phase1_stopped.load() || (control != nullptr && control->stop_requested.load())) {
        return {};
    }

    std::vector<Cluster> clusters;
    for (std::vector<Cluster>& part : partial) {
        clusters.insert(clusters.end(), part.begin(), part.end());
    }
    std::sort(clusters.begin(), clusters.end(), [](const Cluster& a, const Cluster& b) {
        if (a.center_z != b.center_z) {
            return a.center_z < b.center_z;
        }
        return a.center_x < b.center_x;
    });
    clusters.erase(std::unique(clusters.begin(), clusters.end(),
                               [](const Cluster& a, const Cluster& b) {
                                   return a.center_z == b.center_z && a.center_x == b.center_x;
                               }),
                   clusters.end());

    if (request.mode == SearchMode::Fast) {
        clusters.erase(std::remove_if(clusters.begin(), clusters.end(),
                                      [&](const Cluster& cluster) {
                                          return (int64_t)cluster.geode_count * 36 < budding_threshold;
                                      }),
                       clusters.end());
    }

    bool want_budding = request.mode != SearchMode::StructureOnly;
    std::unordered_set<uint64_t> geode_mask;
    if (record_geodes) {
        size_t count = 0;
        for (const std::vector<uint64_t>& part : geode_parts) {
            count += part.size();
        }
        geode_mask.reserve(count);
        for (const std::vector<uint64_t>& part : geode_parts) {
            geode_mask.insert(part.begin(), part.end());
        }
    }
    if (!want_budding) {
        std::vector<Hit> hits;
        hits.reserve(clusters.size());
        for (const Cluster& cluster : clusters) {
            Hit hit;
            hit.block_x = (int32_t)(cluster.center_x * 16);
            hit.block_z = (int32_t)(cluster.center_z * 16);
            hit.budding = -1;
            hit.geode_count = cluster.geode_count;
            hits.push_back(hit);
        }
        std::sort(hits.begin(), hits.end(), [](const Hit& a, const Hit& b) {
            if (a.geode_count != b.geode_count) {
                return a.geode_count > b.geode_count;
            }
            if (a.block_z != b.block_z) {
                return a.block_z < b.block_z;
            }
            return a.block_x < b.block_x;
        });
        return hits;
    }

    if (control != nullptr) {
        control->phase.store(2);
        control->current.store(0);
        control->total.store((long long)clusters.size());
        std::lock_guard<std::mutex> lock(control->result_mutex);
        control->partial.clear();
    }

    int workers_n = std::max(1, std::min(threads, (int)std::max<size_t>(1, clusters.size())));
    size_t chunk = std::max<size_t>(1, (clusters.size() + (size_t)workers_n - 1) / (size_t)workers_n);
    std::vector<std::vector<Hit>> hit_parts(workers_n);
    std::vector<std::thread> phase2;
    for (int i = 0; i < workers_n; ++i) {
        size_t begin = (size_t)i * chunk;
        if (begin >= clusters.size()) {
            break;
        }
        size_t end = std::min(clusters.size(), begin + chunk);
        phase2.emplace_back([&, i, begin, end]() {
            std::vector<Cluster> slice(clusters.begin() + (std::ptrdiff_t)begin, clusters.begin() + (std::ptrdiff_t)end);
            hit_parts[i] = process_clusters(slice, prototype, loaded_radius, budding_threshold, true,
                                             record_geodes ? &geode_mask : nullptr, start_x, end_x, start_z, end_z,
                                             control);
        });
    }
    for (std::thread& worker : phase2) {
        worker.join();
    }

    std::vector<Hit> hits;
    for (std::vector<Hit>& part : hit_parts) {
        hits.insert(hits.end(), part.begin(), part.end());
    }
    std::sort(hits.begin(), hits.end(), [](const Hit& a, const Hit& b) {
        if (a.budding != b.budding) {
            return a.budding > b.budding;
        }
        if (a.block_z != b.block_z) {
            return a.block_z < b.block_z;
        }
        return a.block_x < b.block_x;
    });
    return hits;
}

} // namespace geode
