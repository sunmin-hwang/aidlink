export function paginationItems(current: number, total: number): Array<number | string> {
  const pages = new Set([1, total]);
  for (let page = Math.max(1, current - 2); page <= Math.min(total, current + 2); page += 1) {
    pages.add(page);
  }
  const sorted = [...pages].sort((left, right) => left - right);
  const items: Array<number | string> = [];
  sorted.forEach((page, index) => {
    const previous = sorted[index - 1];
    if (previous && page - previous > 1) items.push(`ellipsis-${previous}`);
    items.push(page);
  });
  return items;
}
