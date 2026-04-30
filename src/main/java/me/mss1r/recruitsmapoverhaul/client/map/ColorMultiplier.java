package me.mss1r.recruitsmapoverhaul.client.map;

record ColorMultiplier(float red, float green, float blue) {
    static ColorMultiplier uniform(float value) {
        return new ColorMultiplier(value, value, value);
    }
}
