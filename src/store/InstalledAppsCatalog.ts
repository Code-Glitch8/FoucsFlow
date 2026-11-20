import { AppCategory } from '../types/models';

export interface CatalogApp {
  packageName: string;
  label: string;
  category: AppCategory;
}

export const INSTALLED_APPS_CATALOG: CatalogApp[] = [
  { packageName: 'com.instagram.android', label: 'Instagram', category: 'SOCIAL' },
  { packageName: 'com.zhiliaoapp.musically', label: 'TikTok', category: 'SOCIAL' },
  { packageName: 'com.snapchat.android', label: 'Snapchat', category: 'SOCIAL' },
  { packageName: 'com.twitter.android', label: 'X (Twitter)', category: 'SOCIAL' },
  { packageName: 'com.reddit.frontpage', label: 'Reddit', category: 'SOCIAL' },
  { packageName: 'com.discord', label: 'Discord', category: 'SOCIAL' },
  { packageName: 'com.facebook.katana', label: 'Facebook', category: 'SOCIAL' },
  { packageName: 'com.instagram.barcelona', label: 'Threads', category: 'SOCIAL' },
  { packageName: 'com.pinterest', label: 'Pinterest', category: 'SOCIAL' },
  { packageName: 'com.google.android.youtube', label: 'YouTube', category: 'ENTERTAINMENT' },
  { packageName: 'com.netflix.mediaclient', label: 'Netflix', category: 'ENTERTAINMENT' },
  { packageName: 'tv.twitch.android.app', label: 'Twitch', category: 'ENTERTAINMENT' },
  { packageName: 'com.google.android.apps.youtube.music', label: 'YouTube Music', category: 'ENTERTAINMENT' },
  { packageName: 'com.spotify.music', label: 'Spotify', category: 'ENTERTAINMENT' },
  { packageName: 'com.disney.disneyplus', label: 'Disney+', category: 'ENTERTAINMENT' },
  { packageName: 'com.roblox.client', label: 'Roblox', category: 'GAME' },
  { packageName: 'com.supercell.brawlstars', label: 'Brawl Stars', category: 'GAME' },
  { packageName: 'com.supercell.clashroyale', label: 'Clash Royale', category: 'GAME' },
  { packageName: 'com.mojang.minecraftpe', label: 'Minecraft', category: 'GAME' },
  { packageName: 'com.innersloth.spacemafia', label: 'Among Us', category: 'GAME' },
  { packageName: 'com.android.chrome', label: 'Chrome', category: 'CUSTOM' },
  { packageName: 'com.whatsapp', label: 'WhatsApp', category: 'CUSTOM' },
  { packageName: 'org.telegram.messenger', label: 'Telegram', category: 'CUSTOM' },
  { packageName: 'com.google.android.gm', label: 'Gmail', category: 'CUSTOM' },
  { packageName: 'com.google.android.apps.docs', label: 'Google Docs', category: 'CUSTOM' },
  { packageName: 'com.snapgpt.assistant', label: 'SnapGPT', category: 'CUSTOM' },
];
