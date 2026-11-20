export const TaskNormalizer = {
  clean(text: string): string {
    return text.trim().replace(/\s+/g, ' ');
  },

  dedupeKey(title: string, chapterId: number | null | undefined): string {
    const norm = TaskNormalizer.clean(title).toLowerCase().replace(/[\s.!?;:,]+$/, '');
    return `${chapterId ?? 0}|${norm}`;
  },
};
