#pragma once

#include "Core.h"

#include <atomic>
#include <cstdint>
#include <mutex>
#include <vector>

namespace geode {

enum class SearchMode : int {
    Full = 0,
    StructureOnly = 1,
    Fast = 2,
};

struct SearchRequest {
    int64_t seed = 0;
    int min_chunk_x = 0;
    int min_chunk_z = 0;
    int max_chunk_x = 0;
    int max_chunk_z = 0;
    int geode_threshold = 20;
    int budding_threshold = 800;
    int loaded_radius = 6;
    int threads = 1;
    VersionId version = VersionId::Mc263;
    SearchMode mode = SearchMode::Full;
};

struct Hit {
    int32_t block_x = 0;
    int32_t block_z = 0;
    int32_t budding = -1;
    int32_t geode_count = 0;
};

struct SearchControl {
    std::atomic<int> phase{0};
    std::atomic<long long> current{0};
    std::atomic<long long> total{0};
    std::atomic<int> status{0};
    std::atomic<bool> pause_requested{false};
    std::atomic<bool> stop_requested{false};
    std::atomic<bool> running{false};
    std::mutex result_mutex;
    std::vector<Hit> partial;
};

SearchControl& search_control();

void reset_search_control();

std::vector<Hit> run_search(const SearchRequest& request, SearchControl* control);

} // namespace geode
