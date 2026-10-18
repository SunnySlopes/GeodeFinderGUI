#pragma once

// Port of geode-finder (math.rs, noise.rs, version.rs, geode.rs).
// Wrapping arithmetic uses uint64_t so signed overflow stays defined.

#include <cmath>
#include <cstdint>
#include <cstring>
#include <algorithm>

namespace geode {

inline int64_t wadd(int64_t a, int64_t b) {
    return (int64_t)((uint64_t)a + (uint64_t)b);
}

inline int64_t wmul(int64_t a, int64_t b) {
    return (int64_t)((uint64_t)a * (uint64_t)b);
}

inline uint64_t rotl64(uint64_t x, int k) {
    return (x << k) | (x >> (64 - k));
}

struct Block {
    int32_t x = 0;
    int32_t y = 0;
    int32_t z = 0;

    static Block make(int32_t x, int32_t y, int32_t z) { return Block{x, y, z}; }

    Block add(int32_t dx, int32_t dy, int32_t dz) const {
        return Block{x + dx, y + dy, z + dz};
    }
};

class JavaRandom {
public:
    static constexpr double DOUBLE_MULTIPLIER = 1.0 / (double)(1ULL << 53);
    static constexpr float FLOAT_MULTIPLIER = 1.0f / (float)(1u << 24);

    JavaRandom() = default;

    static JavaRandom create(int64_t seed) {
        JavaRandom r;
        r.set_seed(seed);
        return r;
    }

    void set_seed(int64_t seed) {
        seed_ = (int64_t)(((uint64_t)seed ^ (uint64_t)MULTIPLIER) & (uint64_t)MOD_MASK);
    }

    int32_t next_bits(uint32_t bits) {
        seed_ = (int64_t)(((uint64_t)seed_ * (uint64_t)MULTIPLIER + (uint64_t)INCREMENT) & (uint64_t)MOD_MASK);
        return (int32_t)((uint64_t)seed_ >> (MOD_BITS - bits));
    }

    static int32_t next_seed_bits(int64_t seed, uint32_t bits) {
        uint64_t next = ((uint64_t)seed ^ (uint64_t)MULTIPLIER) & (uint64_t)MOD_MASK;
        next = (next * (uint64_t)MULTIPLIER + (uint64_t)INCREMENT) & (uint64_t)MOD_MASK;
        return (int32_t)(next >> (MOD_BITS - bits));
    }

    int32_t next_int(int32_t bound) {
        if ((bound & (bound - 1)) == 0) {
            int64_t product = wmul((int64_t)bound, (int64_t)next_bits(31));
            return (int32_t)((uint64_t)product >> 31);
        }
        for (;;) {
            int32_t sample = next_bits(31);
            int32_t modulo = sample % bound;
            int32_t check = (int32_t)((uint32_t)sample - (uint32_t)modulo + (uint32_t)(bound - 1));
            if (check >= 0) {
                return modulo;
            }
        }
    }

    int32_t next_between(int32_t min_inclusive, int32_t max_inclusive) {
        return next_int(max_inclusive - min_inclusive + 1) + min_inclusive;
    }

    int64_t next_long() {
        int64_t upper = (int64_t)next_bits(32);
        int64_t lower = (int64_t)next_bits(32);
        return (int64_t)(((uint64_t)upper << 32) + (uint64_t)lower);
    }

    float next_float() {
        return (float)next_bits(24) * FLOAT_MULTIPLIER;
    }

    double next_double() {
        int64_t upper = (int64_t)next_bits(26);
        int64_t lower = (int64_t)next_bits(27);
        int64_t bits = (int64_t)(((uint64_t)upper << 27) + (uint64_t)lower);
        return (double)bits * DOUBLE_MULTIPLIER;
    }

    void skip(size_t steps) {
        for (size_t i = 0; i < steps; ++i) {
            next_bits(32);
        }
    }

    JavaRandom fork_from_hash() {
        constexpr int64_t HASH = 440898198; // "octave_-4".hashCode()
        return create(HASH ^ next_long());
    }

private:
    static constexpr uint32_t MOD_BITS = 48;
    static constexpr int64_t MOD_MASK = 0xFFFFFFFFFFFFLL;
    static constexpr int64_t MULTIPLIER = 0x5DEECE66DLL;
    static constexpr int64_t INCREMENT = 11;

    int64_t seed_ = 0;
};

class XoroshiroRandom {
public:
    static constexpr float FLOAT_MULTIPLIER = 1.0f / (float)(1u << 24);
    static constexpr double DOUBLE_MULTIPLIER = 1.0 / (double)(1ULL << 53);

    XoroshiroRandom() = default;

    static XoroshiroRandom create(int64_t seed) {
        XoroshiroRandom r;
        r.set_seed(seed);
        return r;
    }

    void set_seed(int64_t seed) {
        int64_t lo = seed ^ SILVER_RATIO;
        int64_t hi = wadd(lo, GOLDEN_RATIO);
        seed_lo_ = splitmix64(lo);
        seed_hi_ = splitmix64(hi);
        if ((seed_lo_ | seed_hi_) == 0) {
            seed_lo_ = GOLDEN_RATIO;
            seed_hi_ = SILVER_RATIO;
        }
    }

    int32_t next_bits(uint32_t bits) {
        uint64_t lo = (uint64_t)seed_lo_;
        uint64_t hi = (uint64_t)seed_hi_;
        uint64_t result = wadd_u(rotl64(lo + hi, 17), lo);
        hi ^= lo;
        seed_lo_ = (int64_t)(rotl64(lo, 49) ^ hi ^ (hi << 21));
        seed_hi_ = (int64_t)rotl64(hi, 28);
        return (int32_t)(uint32_t)(result >> (64 - bits));
    }

    static int32_t next_seed_bits(int64_t seed, uint32_t bits) {
        int64_t lo = seed ^ SILVER_RATIO;
        int64_t hi = wadd(lo, GOLDEN_RATIO);
        lo = splitmix64(lo);
        hi = splitmix64(hi);
        if ((lo | hi) == 0) {
            lo = GOLDEN_RATIO;
            hi = SILVER_RATIO;
        }
        uint64_t result = wadd_u(rotl64((uint64_t)lo + (uint64_t)hi, 17), (uint64_t)lo);
        return (int32_t)(uint32_t)(result >> (64 - bits));
    }

    int32_t next_int(int32_t bound) {
        if ((bound & (bound - 1)) == 0) {
            int64_t product = wmul((int64_t)bound, (int64_t)next_bits(31));
            return (int32_t)((uint64_t)product >> 31);
        }
        for (;;) {
            int32_t sample = next_bits(31);
            int32_t modulo = sample % bound;
            int32_t check = (int32_t)((uint32_t)sample - (uint32_t)modulo + (uint32_t)(bound - 1));
            if (check >= 0) {
                return modulo;
            }
        }
    }

    int32_t next_between(int32_t min_inclusive, int32_t max_inclusive) {
        return next_int(max_inclusive - min_inclusive + 1) + min_inclusive;
    }

    int64_t next_long() {
        int64_t upper = (int64_t)next_bits(32);
        int64_t lower = (int64_t)next_bits(32);
        return (int64_t)(((uint64_t)upper << 32) + (uint64_t)lower);
    }

    float next_float() {
        return (float)next_bits(24) * FLOAT_MULTIPLIER;
    }

    double next_double() {
        int64_t upper = (int64_t)next_bits(26);
        int64_t lower = (int64_t)next_bits(27);
        int64_t bits = (int64_t)(((uint64_t)upper << 27) + (uint64_t)lower);
        return (double)bits * DOUBLE_MULTIPLIER;
    }

    void skip(size_t steps) {
        for (size_t i = 0; i < steps; ++i) {
            next_bits(32);
        }
    }

private:
    static constexpr int64_t GOLDEN_RATIO = (int64_t)0x9E3779B97F4A7C15ULL;
    static constexpr int64_t SILVER_RATIO = (int64_t)0x6A09E667F3BCC909ULL;
    static constexpr uint64_t STAFFORD_MIX13_1 = 0xBF58476D1CE4E5B9ULL;
    static constexpr uint64_t STAFFORD_MIX13_2 = 0x94D049BB133111EBULL;

    static int64_t splitmix64(int64_t z_signed) {
        uint64_t z = (uint64_t)z_signed;
        z = (z ^ (z >> 30)) * STAFFORD_MIX13_1;
        z = (z ^ (z >> 27)) * STAFFORD_MIX13_2;
        z = z ^ (z >> 31);
        return (int64_t)z;
    }

    static uint64_t wadd_u(uint64_t a, uint64_t b) { return a + b; }

    int64_t seed_lo_ = 0;
    int64_t seed_hi_ = 0;
};

inline double quake_inv_sqrt(double x) {
    uint64_t bits;
    static_assert(sizeof(double) == sizeof(uint64_t), "double must be 64-bit");
    std::memcpy(&bits, &x, sizeof(bits));
    bits = 0x5FE6EB50C7B537AAULL - (bits >> 1);
    double i;
    std::memcpy(&i, &bits, sizeof(i));
    return i * (1.5 - (0.5 * x) * i * i);
}

inline double real_inv_sqrt(double x) {
    return 1.0 / std::sqrt(x);
}

enum class VersionId : int {
    Mc17 = 0,
    Mc18 = 1,
    Mc182 = 2,
    Mc194 = 3,
    Mc263 = 4,
};

template <typename F>
struct PerlinNoise {
    double x_offset = 0;
    double y_offset = 0;
    double z_offset = 0;
    uint8_t permutation[256]{};

    static constexpr double NOISE_OFFSET_SCALE = 256.0;
    static constexpr double ROUND_OFF = (double)(1 << 25);
    static double half_round_off() {
        return std::nextafter((double)(1 << 24), 0.0);
    }

    static PerlinNoise create(JavaRandom& random) {
        PerlinNoise noise;
        noise.x_offset = random.next_double() * NOISE_OFFSET_SCALE;
        noise.y_offset = random.next_double() * NOISE_OFFSET_SCALE;
        noise.z_offset = random.next_double() * NOISE_OFFSET_SCALE;
        for (int i = 0; i < 256; ++i) {
            noise.permutation[i] = (uint8_t)i;
        }
        for (int i = 0; i < 256; ++i) {
            int offset = random.next_int(256 - i);
            std::swap(noise.permutation[i], noise.permutation[i + offset]);
        }
        return noise;
    }

    int permute(int i) const { return (int)permutation[i & 0xFF]; }

    static F grad_dot(int hash, F x, F y, F z) {
        static constexpr F ONE = (F)1;
        static constexpr F NEG_ONE = (F)-1;
        static constexpr F ZERO = (F)0;
        static constexpr F GRADIENT[16][3] = {
            {ONE, ONE, ZERO},     {NEG_ONE, ONE, ZERO},    {ONE, NEG_ONE, ZERO},    {NEG_ONE, NEG_ONE, ZERO},
            {ONE, ZERO, ONE},     {NEG_ONE, ZERO, ONE},    {ONE, ZERO, NEG_ONE},    {NEG_ONE, ZERO, NEG_ONE},
            {ZERO, ONE, ONE},     {ZERO, NEG_ONE, ONE},    {ZERO, ONE, NEG_ONE},    {ZERO, NEG_ONE, NEG_ONE},
            {ONE, ONE, ZERO},     {ZERO, NEG_ONE, ONE},    {NEG_ONE, ONE, ZERO},    {ZERO, NEG_ONE, NEG_ONE},
        };
        const F* g = GRADIENT[hash & 0xF];
        return g[0] * x + g[1] * y + g[2] * z;
    }

    static double wrap(double value) {
        double half = half_round_off();
        if (value > -half && value < half) {
            return value;
        }
        return value - std::floor(value / ROUND_OFF + 0.5) * ROUND_OFF;
    }

    static F smoothstep(F x) {
        F six = (F)6;
        F fifteen = (F)15;
        F ten = (F)10;
        return x * x * x * (x * (x * six - fifteen) + ten);
    }

    static F lerp(F tx, F v0, F v1) { return v0 + tx * (v1 - v0); }

    static F bilerp(F tx, F ty, const F corners[4]) {
        return lerp(ty, lerp(tx, corners[0], corners[1]), lerp(tx, corners[2], corners[3]));
    }

    static F trilerp(F tx, F ty, F tz, const F corners[8]) {
        F xy0[4] = {corners[0], corners[1], corners[2], corners[3]};
        F xy1[4] = {corners[4], corners[5], corners[6], corners[7]};
        return lerp(tz, bilerp(tx, ty, xy0), bilerp(tx, ty, xy1));
    }

    F sample_lerp(int whole[3], F frac[3], F raw_dy) const {
        int x = whole[0];
        int y = whole[1];
        int z = whole[2];
        F dx = frac[0];
        F dy = frac[1];
        F dz = frac[2];
        F one = (F)1;

        int v0 = permute(x);
        int v1 = permute(x + 1);
        int v00 = permute(v0 + y);
        int v10 = permute(v1 + y);
        int v01 = permute(v0 + y + 1);
        int v11 = permute(v1 + y + 1);

        F corners[8] = {
            grad_dot(permute(v00 + z), dx, dy, dz),
            grad_dot(permute(v10 + z), dx - one, dy, dz),
            grad_dot(permute(v01 + z), dx, dy - one, dz),
            grad_dot(permute(v11 + z), dx - one, dy - one, dz),
            grad_dot(permute(v00 + z + 1), dx, dy, dz - one),
            grad_dot(permute(v10 + z + 1), dx - one, dy, dz - one),
            grad_dot(permute(v01 + z + 1), dx, dy - one, dz - one),
            grad_dot(permute(v11 + z + 1), dx - one, dy - one, dz - one),
        };
        return trilerp(smoothstep(dx), smoothstep(raw_dy), smoothstep(dz), corners);
    }

    F get(double x, double y, double z) const {
        double offset_x = wrap(x) + x_offset;
        double offset_y = wrap(y) + y_offset;
        double offset_z = wrap(z) + z_offset;
        double floor_x = std::floor(offset_x);
        double floor_y = std::floor(offset_y);
        double floor_z = std::floor(offset_z);
        int whole[3] = {(int)floor_x, (int)floor_y, (int)floor_z};
        F frac[3] = {(F)(offset_x - floor_x), (F)(offset_y - floor_y), (F)(offset_z - floor_z)};
        return sample_lerp(whole, frac, frac[1]);
    }
};

struct VersionSpec {
    bool use_xoroshiro;
    bool distance_from_center;
    bool quake_inv_sqrt;
    bool unnormalized_noise;
    bool perlin_fork;
    bool amplitude_separate;
    bool float_noise;
    int64_t salt;
    float chance;
    int y_min;
    int y_max;
};

inline VersionSpec spec_for(VersionId id) {
    switch (id) {
    case VersionId::Mc17:
        return {false, true, true, true, false, false, false, 20000, 1.0f / 53.0f, 6, 46};
    case VersionId::Mc18:
        return {true, true, true, true, true, false, false, 20002, 1.0f / 24.0f, -58, 30};
    case VersionId::Mc182:
        return {true, false, true, true, true, false, false, 20002, 1.0f / 24.0f, -58, 30};
    case VersionId::Mc194:
        return {true, false, false, true, true, false, false, 20002, 1.0f / 24.0f, -58, 30};
    case VersionId::Mc263:
    default:
        return {true, false, false, false, true, true, true, 20002, 1.0f / 24.0f, -58, 30};
    }
}

inline int32_t chance_int(float chance) {
    constexpr float recip = (float)(1u << 24);
    return (int32_t)(chance * recip);
}

template <typename F>
struct NormalNoise {
    PerlinNoise<F> first{};
    PerlinNoise<F> second{};
    double freq1 = 0;
    double freq2 = 0;
    F value_factor = 0;
    bool amplitude_separate = false;
    double output_abs_bound = 0;

    static constexpr double PERSISTENCE = 0.5;
    static constexpr double LACUNARITY = 2.0;
    static constexpr double PERLIN_DEVIATION = 0.2702247831245211;
    static constexpr double TARGET_DEVIATION = 1.0 / 3.0;
    static constexpr double PARITY_EXPECTED_DEVIATION = 0.2;
    static constexpr double INPUT_FACTOR = 1.0181268882175227;

    static double base_amplitude(double amplitude) {
        return amplitude * PERSISTENCE * TARGET_DEVIATION / PARITY_EXPECTED_DEVIATION;
    }

    static double normalization(double target_amplitude) {
        double input_deviation = PERLIN_DEVIATION * std::fabs(target_amplitude) * std::sqrt(2.0);
        double target_deviation = target_amplitude * TARGET_DEVIATION;
        return target_deviation / input_deviation;
    }

    static double parity_base_amplitude(double amplitude) {
        double target_amplitude = std::fabs(amplitude);
        double new_normalization = normalization(target_amplitude);
        double old_normalization = base_amplitude(target_amplitude);
        return old_normalization / new_normalization;
    }

    static double frequency(int octave) {
        return std::ldexp(1.0, octave);
    }

    static NormalNoise create(const VersionSpec& spec, int octave, double amplitude) {
        JavaRandom random = JavaRandom::create(0);
        return create_from(spec, random, octave, amplitude);
    }

    static NormalNoise create_from(const VersionSpec& spec, JavaRandom& random, int octave, double amplitude) {
        NormalNoise noise;
        noise.amplitude_separate = spec.amplitude_separate;
        PerlinNoise<F> perlin1;
        PerlinNoise<F> perlin2;
        if (spec.perlin_fork) {
            JavaRandom fork1 = random.fork_from_hash();
            JavaRandom fork2 = random.fork_from_hash();
            perlin1 = PerlinNoise<F>::create(fork1);
            perlin2 = PerlinNoise<F>::create(fork2);
        } else {
            size_t steps = (size_t)262 * (size_t)(octave < 0 ? -octave : octave);
            random.skip(steps);
            perlin1 = PerlinNoise<F>::create(random);
            random.skip(steps);
            perlin2 = PerlinNoise<F>::create(random);
        }
        double freq = frequency(octave);
        double parity = parity_base_amplitude(amplitude);
        double norm = normalization(parity);
        double factor = spec.unnormalized_noise ? base_amplitude(amplitude) : parity * norm;
        noise.first = perlin1;
        noise.second = perlin2;
        noise.freq1 = freq;
        noise.freq2 = freq * INPUT_FACTOR;
        noise.value_factor = (F)factor;
        // Each Perlin sample is strictly inside (-2, 2). 2.25 leaves room for rounding.
        noise.output_abs_bound = std::fabs((double)noise.value_factor) * 4.5;
        return noise;
    }

    F get(double x, double y, double z) const {
        F layer1 = first.get(x * freq1, y * freq1, z * freq1);
        F layer2 = second.get(x * freq2, y * freq2, z * freq2);
        if (amplitude_separate) {
            return value_factor * layer1 + value_factor * layer2;
        }
        return value_factor * (layer1 + layer2);
    }
};

inline double distance_sq(const Block& a, const Block& b, bool from_center) {
    double dx = (double)(a.x - b.x);
    double dy = (double)(a.y - b.y);
    double dz = (double)(a.z - b.z);
    if (from_center) {
        dx += 0.5;
        dy += 0.5;
        dz += 0.5;
    }
    return dx * dx + dy * dy + dz * dz;
}

struct GeodeGen {
    VersionSpec spec{};
    int64_t seed = 0;
    int64_t x_scale = 0;
    int64_t z_scale = 0;
    bool use_xoroshiro = false;
    JavaRandom java_random{};
    XoroshiroRandom xoro_random{};
    NormalNoise<double> noise_f64{};
    NormalNoise<float> noise_f32{};
    bool float_noise = false;
    int32_t chance_limit = 0;
    double noise_abs_bound = 0;

    static GeodeGen create(VersionId id, int64_t seed) {
        GeodeGen g;
        g.spec = spec_for(id);
        g.seed = seed;
        g.use_xoroshiro = g.spec.use_xoroshiro;
        g.float_noise = g.spec.float_noise;
        g.chance_limit = chance_int(g.spec.chance);
        if (g.use_xoroshiro) {
            g.xoro_random = XoroshiroRandom::create(seed);
        } else {
            g.java_random = JavaRandom::create(seed);
        }
        JavaRandom noise_random = JavaRandom::create(seed);
        if (g.float_noise) {
            g.noise_f32 = NormalNoise<float>::create_from(g.spec, noise_random, -4, 1.0);
            g.noise_abs_bound = g.noise_f32.output_abs_bound;
        } else {
            g.noise_f64 = NormalNoise<double>::create_from(g.spec, noise_random, -4, 1.0);
            g.noise_abs_bound = g.noise_f64.output_abs_bound;
        }
        int64_t first = g.next_long() | 1;
        int64_t second = g.next_long() | 1;
        g.x_scale = wmul(first, 16);
        g.z_scale = wmul(second, 16);
        return g;
    }

    int64_t scale_z(int64_t chunk_z) const { return wmul(chunk_z, z_scale); }

    int64_t feature_seed(int64_t chunk_x, int64_t scaled_z) const {
        int64_t scaled_x = wmul(chunk_x, x_scale);
        int64_t decoration = wadd(scaled_x, scaled_z) ^ seed;
        return wadd(decoration, spec.salt);
    }

    bool check(int64_t chunk_x, int64_t chunk_z) {
        int64_t feature = feature_seed(chunk_x, scale_z(chunk_z));
        set_seed(feature);
        return next_float() < spec.chance;
    }

    bool check_fast(int64_t chunk_x, int64_t scaled_z) const {
        int64_t feature = feature_seed(chunk_x, scaled_z);
        int32_t bits = use_xoroshiro ? XoroshiroRandom::next_seed_bits(feature, 24)
                                      : JavaRandom::next_seed_bits(feature, 24);
        return bits <= chance_limit;
    }

    double noise_at(double x, double y, double z) const { return sample_noise(x, y, z); }

    // Returns budding count, or -1 when the chunk has no geode.
    int generate(int64_t chunk_x, int64_t chunk_z) {
        if (!check(chunk_x, chunk_z)) {
            return -1;
        }

        int32_t origin_x = next_int(16) + (int32_t)chunk_x * 16;
        int32_t origin_z = next_int(16) + (int32_t)chunk_z * 16;
        int32_t origin_y = next_between(spec.y_min, spec.y_max);
        Block origin = Block::make(origin_x, origin_y, origin_z);

        int num_points = next_between(3, 4);
        double num_points_f = (double)num_points;
        double crack_size_adjustment = num_points_f / 6.0;

        double air_dist = real_inv_sqrt(1.7);
        double amethyst_dist = real_inv_sqrt(2.2 + crack_size_adjustment);
        double basalt_dist = real_inv_sqrt(4.2 + crack_size_adjustment);

        double crack_size = real_inv_sqrt(
            2.0 + next_double() / 2.0 + crack_size_adjustment * (num_points > 3 ? 1.0 : 0.0));
        bool should_generate_crack = (double)next_float() < 0.95;

        Block points[4];
        double offsets[4];
        for (int i = 0; i < num_points; ++i) {
            // Argument order is unspecified; the reference consumes x, then y, then z.
            int32_t dx = next_between(4, 6);
            int32_t dy = next_between(4, 6);
            int32_t dz = next_between(4, 6);
            points[i] = origin.add(dx, dy, dz);
            offsets[i] = (double)next_between(1, 2);
        }

        Block crack_points[3];
        if (should_generate_crack) {
            int crack = num_points * 2 + 1;
            int cracks[4][2] = {{crack, 0}, {0, crack}, {crack, crack}, {0, 0}};
            int pick = next_int(4);
            int dx = cracks[pick][0];
            int dz = cracks[pick][1];
            int dys[3] = {7, 5, 1};
            for (int i = 0; i < 3; ++i) {
                crack_points[i] = origin.add(dx, dys[i], dz);
            }
        }

        // Noise is in [-bound, bound]. Skip Perlin only when the shell cannot enter the solid band.
        double shell_noise_lim = noise_abs_bound * 0.05 * num_points_f;
        auto inv = [&](double v) { return spec.quake_inv_sqrt ? quake_inv_sqrt(v) : real_inv_sqrt(v); };
        int budding_count = 0;
        for (int z = origin.z - 16; z <= origin.z + 16; ++z) {
            for (int y = origin.y - 16; y <= origin.y + 16; ++y) {
                for (int x = origin.x - 16; x <= origin.x + 16; ++x) {
                    Block block = Block::make(x, y, z);
                    double geom = 0;
                    for (int i = 0; i < num_points; ++i) {
                        geom += inv(distance_sq(block, points[i], spec.distance_from_center) + offsets[i]);
                    }
                    if (geom + shell_noise_lim < basalt_dist || geom - shell_noise_lim >= air_dist) {
                        continue;
                    }

                    double noise_offset = sample_noise((double)x, (double)y, (double)z) * 0.05;
                    double shell = geom + noise_offset * num_points_f;
                    bool in_solid = shell >= basalt_dist && shell < air_dist;
                    if (!in_solid) {
                        continue;
                    }

                    double crack_sum = 0;
                    if (should_generate_crack) {
                        for (int i = 0; i < 3; ++i) {
                            crack_sum += inv(distance_sq(block, crack_points[i], spec.distance_from_center) + 2.0);
                        }
                        crack_sum += noise_offset * 3.0;
                    }
                    bool in_crack = should_generate_crack && crack_sum >= crack_size;
                    if (!in_crack && shell >= amethyst_dist) {
                        bool place = (double)next_float() < 0.083;
                        if (place) {
                            budding_count += 1;
                            skip(1);
                        }
                    }
                }
            }
        }
        return budding_count;
    }

private:
    double sample_noise(double x, double y, double z) const {
        if (float_noise) {
            return (double)noise_f32.get(x, y, z);
        }
        return noise_f64.get(x, y, z);
    }

    void set_seed(int64_t feature) {
        if (use_xoroshiro) {
            xoro_random.set_seed(feature);
        } else {
            java_random.set_seed(feature);
        }
    }

    int32_t next_bits(uint32_t bits) {
        return use_xoroshiro ? xoro_random.next_bits(bits) : java_random.next_bits(bits);
    }

    int32_t next_int(int32_t bound) {
        return use_xoroshiro ? xoro_random.next_int(bound) : java_random.next_int(bound);
    }

    int32_t next_between(int32_t lo, int32_t hi) {
        return use_xoroshiro ? xoro_random.next_between(lo, hi) : java_random.next_between(lo, hi);
    }

    int64_t next_long() {
        return use_xoroshiro ? xoro_random.next_long() : java_random.next_long();
    }

    float next_float() {
        return use_xoroshiro ? xoro_random.next_float() : java_random.next_float();
    }

    double next_double() {
        return use_xoroshiro ? xoro_random.next_double() : java_random.next_double();
    }

    void skip(size_t steps) {
        if (use_xoroshiro) {
            xoro_random.skip(steps);
        } else {
            java_random.skip(steps);
        }
    }
};

} // namespace geode
