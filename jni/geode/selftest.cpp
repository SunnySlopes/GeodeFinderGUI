#include "geode/Core.h"
#include "geode/Search.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>

namespace {

int g_failed = 0;

void expect(bool ok, const char* message) {
    if (!ok) {
        std::fprintf(stderr, "FAIL: %s\n", message);
        ++g_failed;
    }
}

void expect_i64(int64_t actual, int64_t expected, const char* message) {
    if (actual != expected) {
        std::fprintf(stderr, "FAIL: %s (actual %lld expected %lld)\n", message, (long long)actual, (long long)expected);
        ++g_failed;
    }
}

void expect_u32(uint32_t actual, uint32_t expected, const char* message) {
    if (actual != expected) {
        std::fprintf(stderr, "FAIL: %s (actual %u expected %u)\n", message, actual, expected);
        ++g_failed;
    }
}

template <typename T>
void expect_near(T actual, T expected, const char* message) {
    if (actual != expected) {
        std::fprintf(stderr, "FAIL: %s (actual %.17g expected %.17g)\n", message, (double)actual, (double)expected);
        ++g_failed;
    }
}

void test_random() {
    using geode::JavaRandom;
    using geode::XoroshiroRandom;
    constexpr int64_t SEED = 0xDEADBEEFLL;

    JavaRandom random = JavaRandom::create(SEED);
    expect_i64(random.next_int(256), 33, "next_int power-of-two");
    expect_i64(random.next_int(192), 152, "next_int non power-of-two");
    expect_i64(random.next_between(192, 256), 222, "next_between");
    expect_i64(random.next_long(), 1683040361022731026LL, "next_long");
    expect_near(random.next_float(), 0.8564069f, "next_float");
    expect_near(random.next_double(), 0.544608645520025, "next_double");
    random.skip(256);
    expect_i64(random.next_int(256), 103, "skip");

    random = JavaRandom::create(SEED);
    int32_t bits = 0;
    for (int i = 0; i < 256; ++i) {
        bits = random.next_bits(32);
    }
    expect_i64(bits, -1183353915, "java random progression");

    random = JavaRandom::create(SEED);
    expect_i64(random.fork_from_hash().next_long(), -3702631657396612959LL, "fork");

    XoroshiroRandom xoro = XoroshiroRandom::create(SEED);
    int32_t xbits = 0;
    for (int i = 0; i < 256; ++i) {
        xbits = xoro.next_bits(32);
    }
    expect_i64(xbits, 1573955217, "xoroshiro progression");
}

void test_noise() {
    using geode::JavaRandom;
    using geode::NormalNoise;
    using geode::PerlinNoise;
    using geode::VersionId;
    using geode::spec_for;
    constexpr int64_t SEED = 0xDEADBEEFLL;

    JavaRandom random = JavaRandom::create(SEED);
    PerlinNoise<double> perlin = PerlinNoise<double>::create(random);
    expect_near(perlin.get(0, 0, 0), 0.11848800878838489, "perlin f64 0");
    expect_near(perlin.get(-0.5, -0.5, -0.5), -0.08726343323690666, "perlin f64 -0.5");
    expect_near(perlin.get(-29999999.5, 64.25, 29999999.125), -0.34497749247741055, "perlin f64 border");

    random = JavaRandom::create(SEED);
    PerlinNoise<float> perlin_f = PerlinNoise<float>::create(random);
    expect_near(perlin_f.get(0, 0, 0), 0.118487954f, "perlin f32 0");
    expect_near(perlin_f.get(-0.5, -0.5, -0.5), -0.087263405f, "perlin f32 -0.5");
    expect_near(perlin_f.get(-29999999.5, 64.25, 29999999.125), -0.3449775f, "perlin f32 border");

    auto check_normal = [](VersionId id, double zero, double negative, double mixed, const char* name) {
        auto spec = spec_for(id);
        JavaRandom seeded = JavaRandom::create(SEED);
        if (spec.float_noise) {
            NormalNoise<float> noise = NormalNoise<float>::create_from(spec, seeded, -4, 1.0);
            double scale = 1.0 / noise.freq2;
            expect_near(noise.get(0, 0, 0), (float)zero, name);
            expect_near(noise.get(-scale, -scale, -scale), (float)negative, name);
            expect_near(noise.get(0.5 * scale, 0.25 * scale, 0.125 * scale), (float)mixed, name);
        } else {
            NormalNoise<double> noise = NormalNoise<double>::create_from(spec, seeded, -4, 1.0);
            double scale = 1.0 / noise.freq2;
            expect_near(noise.get(0, 0, 0), zero, name);
            expect_near(noise.get(-scale, -scale, -scale), negative, name);
            expect_near(noise.get(0.5 * scale, 0.25 * scale, 0.125 * scale), mixed, name);
        }
    };
    check_normal(VersionId::Mc17, -0.13714612453645852, 0.2836656908436528, 0.009630604775887711, "normal 1.17");
    check_normal(VersionId::Mc18, -0.29788683109182007, -0.09486360661852414, 0.13893713975091695, "normal 1.18");

    {
        auto spec = spec_for(VersionId::Mc263);
        JavaRandom seeded = JavaRandom::create(SEED);
        NormalNoise<float> noise = NormalNoise<float>::create_from(spec, seeded, -4, 1.0);
        double scale = 1.0 / noise.freq2;
        expect_near(noise.get(0, 0, 0), -0.2978868f, "normal 26.3 zero");
        expect_near(noise.get(-scale, -scale, -scale), -0.094863616f, "normal 26.3 negative");
        expect_near(noise.get(0.5 * scale, 0.25 * scale, 0.125 * scale), 0.13893716f, "normal 26.3 mixed");
    }
}

struct Expected {
    uint32_t geodes;
    uint32_t budding;
};

void test_generate(geode::VersionId id, Expected expected, const char* name) {
    constexpr int64_t SEED = 0xDEADBEEFLL;
    constexpr int64_t RANGE = 32;
    geode::GeodeGen gen = geode::GeodeGen::create(id, SEED);
    uint32_t geode_count = 0;
    uint32_t fast_count = 0;
    uint32_t budding = 0;
    for (int64_t z = -RANGE; z <= RANGE; ++z) {
        int64_t scaled_z = gen.scale_z(z);
        for (int64_t x = -RANGE; x <= RANGE; ++x) {
            geode_count += gen.check(x, z) ? 1u : 0u;
            fast_count += gen.check_fast(x, scaled_z) ? 1u : 0u;
            int generated = gen.generate(x, z);
            if (generated > 0) {
                budding += (uint32_t)generated;
            }
        }
    }
    std::string geode_msg = std::string(name) + " geode count";
    std::string bud_msg = std::string(name) + " budding";
    std::string fast_msg = std::string(name) + " check_fast";
    expect_u32(geode_count, expected.geodes, geode_msg.c_str());
    expect_u32(budding, expected.budding, bud_msg.c_str());
    expect_u32(fast_count, geode_count, fast_msg.c_str());
    std::printf("%s: geodes %u budding %u\n", name, geode_count, budding);
}

void test_search() {
    geode::SearchRequest request;
    request.seed = 0;
    request.min_chunk_x = -1000;
    request.min_chunk_z = -1000;
    request.max_chunk_x = 1000;
    request.max_chunk_z = 1000;
    request.geode_threshold = 20;
    request.budding_threshold = 800;
    request.loaded_radius = 6;
    request.threads = 1;
    request.version = geode::VersionId::Mc194;
    request.mode = geode::SearchMode::StructureOnly;

    std::printf("structure search...\n");
    std::vector<geode::Hit> structures = geode::run_search(request, nullptr);
    int max_geodes = 0;
    for (const geode::Hit& hit : structures) {
        max_geodes = std::max(max_geodes, hit.geode_count);
        expect(hit.budding < 0, "structure hit has no budding");
    }
    expect_i64((int64_t)structures.size(), 189, "structure cluster count");
    expect_i64(max_geodes, 23, "max geode count");
    std::printf("structures %zu max geodes %d\n", structures.size(), max_geodes);
    if (g_failed) {
        return;
    }

    std::printf("full search...\n");
    request.mode = geode::SearchMode::Full;
    std::vector<geode::Hit> full = geode::run_search(request, nullptr);
    int max_budding = 0;
    for (const geode::Hit& hit : full) {
        max_budding = std::max(max_budding, hit.budding);
    }
    expect_i64((int64_t)full.size(), 40, "budding cluster count");
    expect_i64(max_budding, 911, "max budding");
    std::printf("full %zu max budding %d\n", full.size(), max_budding);

    std::printf("fast search...\n");
    request.mode = geode::SearchMode::Fast;
    std::vector<geode::Hit> fast = geode::run_search(request, nullptr);
    for (const geode::Hit& hit : fast) {
        expect(hit.geode_count * 36 >= 800, "fast hit below estimate cutoff");
        bool found = false;
        for (const geode::Hit& exact : full) {
            if (exact.block_x == hit.block_x && exact.block_z == hit.block_z) {
                expect_i64(hit.budding, exact.budding, "fast budding mismatch");
                found = true;
                break;
            }
        }
        expect(found, "fast hit missing from full search");
    }
    std::printf("fast %zu\n", fast.size());
}

} // namespace

int main(int argc, char** argv) {
    bool quick = argc > 1 && std::string(argv[1]) == "quick";
    test_random();
    if (g_failed) {
        return 1;
    }
    test_noise();
    if (g_failed) {
        return 1;
    }
    std::printf("rng and noise ok\n");
    if (quick) {
        return 0;
    }
    test_generate(geode::VersionId::Mc17, {72, 2567}, "1.17");
    test_generate(geode::VersionId::Mc18, {158, 5732}, "1.18");
    test_generate(geode::VersionId::Mc182, {158, 5722}, "1.18.2");
    test_generate(geode::VersionId::Mc194, {158, 5736}, "1.19.4");
    test_generate(geode::VersionId::Mc263, {158, 5736}, "26.3");
    if (g_failed) {
        return 1;
    }
    test_search();
    if (g_failed) {
        std::fprintf(stderr, "%d failure(s)\n", g_failed);
        return 1;
    }
    std::printf("all tests passed\n");
    return 0;
}
