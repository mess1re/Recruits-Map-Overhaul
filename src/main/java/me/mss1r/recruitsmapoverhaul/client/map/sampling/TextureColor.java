package me.mss1r.recruitsmapoverhaul.client.map.sampling;

record TextureColor(int rgb, int tintIndex) {
    static final TextureColor EMPTY = new TextureColor(0, -1);
}
