'use strict';

const path = require('path');
const net = require('./net');
const { readJson, writeJson } = require('./store');

const FEED = 'https://launchercontent.mojang.com/v2/news.json';
const CONTENT = 'https://launchercontent.mojang.com';
const TTL = 30 * 60 * 1000;

/**
 * Java Edition news from the feed the official launcher shows, cached on disk for offline starts.
 * Aurora's own announcements ship with the launcher and come first.
 */
class News {
  constructor({ cacheDir, bundled }) {
    this.file = path.join(cacheDir, 'news.json');
    this.bundled = bundled;
    this.memory = null;
  }

  async minecraft() {
    if (this.memory && Date.now() - this.memory.at < TTL) return this.memory.items;
    let feed;
    try {
      feed = await net.getJson(FEED, { timeout: 12000, retries: 1 });
      writeJson(this.file, feed);
    } catch {
      feed = readJson(this.file, { entries: [] });
    }
    const items = (feed.entries || [])
      .filter((e) => (e.newsType || []).includes('Java'))
      .slice(0, 8)
      .map((e) => ({
        id: e.id,
        source: 'minecraft',
        title: String(e.title || '').trim(),
        text: String(e.text || '').trim(),
        tag: e.tag || 'News',
        date: e.date,
        image: e.newsPageImage ? CONTENT + e.newsPageImage.url : e.playPageImage ? CONTENT + e.playPageImage.url : null,
        link: e.readMoreLink || null,
      }));
    this.memory = { at: Date.now(), items };
    return items;
  }

  async all() {
    let minecraft = [];
    try {
      minecraft = await this.minecraft();
    } catch {
      minecraft = [];
    }
    return [...this.bundled, ...minecraft.slice(0, 4)];
  }
}

module.exports = { News };
