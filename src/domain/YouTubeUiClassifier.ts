export interface UiNode {
  viewId?: string | null;
  text?: string | null;
  contentDescription?: string | null;
  className?: string | null;
  selected?: boolean;
  children?: UiNode[];
}

export type YouTubeScreen = 'SHORTS_PLAYER' | 'HOME_FEED' | 'OTHER';

export interface YouTubeClassification {
  screen: YouTubeScreen;
  signals: string[];
}

const SHORTS_PLAYER_ID_PREFIXES = [
  'reel_recycler',
  'reel_player_page_container',
  'reel_player_overlay',
  'reel_watch_fragment_root',
  'reel_time_bar',
  'reel_progress_bar',
  'shorts_player',
];

const SHORTS_TAB_LABELS = new Set(['shorts']);
const HOME_TAB_LABELS = new Set(['home']);
const SUBSCRIPTIONS_TAB_LABELS = new Set(['subscriptions']);

function shortIdOf(viewId?: string | null): string | null {
  if (!viewId) return null;
  const idx = viewId.indexOf(':id/');
  return idx >= 0 ? viewId.slice(idx + 4) : viewId;
}

function labelOf(node: UiNode): string | null {
  if (node.contentDescription && node.contentDescription.trim().length > 0) {
    return node.contentDescription;
  }
  if (node.text && node.text.trim().length > 0) {
    return node.text;
  }
  return null;
}

function* walkNodes(root: UiNode): Generator<UiNode> {
  yield root;
  if (root.children) {
    for (const child of root.children) {
      yield* walkNodes(child);
    }
  }
}

export const YouTubeUiClassifier = {
  PACKAGE: 'com.google.android.youtube',

  classify(root: UiNode): YouTubeClassification {
    const signals: string[] = [];
    let shorts = false;

    for (const n of walkNodes(root)) {
      const id = shortIdOf(n.viewId);
      if (id && SHORTS_PLAYER_ID_PREFIXES.some((prefix) => id.startsWith(prefix))) {
        signals.push(`Shorts player view id: ${id}`);
        shorts = true;
      }
      const label = labelOf(n)?.trim().toLowerCase();
      if (n.selected && label && SHORTS_TAB_LABELS.has(label)) {
        signals.push('Shorts tab is selected');
        shorts = true;
      }
    }

    if (shorts) {
      return {
        screen: 'SHORTS_PLAYER',
        signals: Array.from(new Set(signals)),
      };
    }

    for (const n of walkNodes(root)) {
      const label = labelOf(n)?.trim().toLowerCase();
      if (n.selected && label && HOME_TAB_LABELS.has(label)) {
        return {
          screen: 'HOME_FEED',
          signals: ['Home tab is selected'],
        };
      }
    }

    return {
      screen: 'OTHER',
      signals: [],
    };
  },

  isHomeTabLabel(label?: string | null): boolean {
    return !!label && HOME_TAB_LABELS.has(label.trim().toLowerCase());
  },

  isSubscriptionsTabLabel(label?: string | null): boolean {
    return !!label && SUBSCRIPTIONS_TAB_LABELS.has(label.trim().toLowerCase());
  },

  sampleIds(root: UiNode, limit = 40): string[] {
    const seen = new Set<string>();
    for (const n of walkNodes(root)) {
      const id = shortIdOf(n.viewId);
      if (id) {
        seen.add(id);
        if (seen.size >= limit) break;
      }
    }
    return Array.from(seen);
  },
};
