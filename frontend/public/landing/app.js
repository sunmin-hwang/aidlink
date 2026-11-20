const menuButton = document.querySelector("[data-menu-button]");
const menu = document.querySelector("[data-menu]");

if (menuButton && menu) {
  menuButton.addEventListener("click", () => {
    const isOpen = menuButton.getAttribute("aria-expanded") === "true";
    menuButton.setAttribute("aria-expanded", String(!isOpen));
    menu.dataset.open = String(!isOpen);
  });

  menu.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", () => {
      menuButton.setAttribute("aria-expanded", "false");
      menu.dataset.open = "false";
    });
  });
}

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const revealTargets = document.querySelectorAll("[data-reveal]");

if (reduceMotion || !("IntersectionObserver" in window)) {
  revealTargets.forEach((element) => element.classList.add("is-visible"));
} else {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        }
      });
    },
    { rootMargin: "0px 0px -8%", threshold: 0.12 },
  );

  revealTargets.forEach((element) => observer.observe(element));
}

const panels = [...document.querySelectorAll("[data-panel]")];
const panelLinks = [...document.querySelectorAll("[data-panel-link]")];

const setActivePanel = (panelId) => {
  panelLinks.forEach((link) => {
    const isCurrent = link.getAttribute("href") === `#${panelId}`;
    if (isCurrent) {
      link.setAttribute("aria-current", "page");
    } else {
      link.removeAttribute("aria-current");
    }
  });
};

document.querySelectorAll('a[href^="#"]').forEach((link) => {
  const targetId = link.getAttribute("href")?.slice(1);
  const target = targetId ? document.getElementById(targetId) : null;

  if (!target?.matches("[data-panel]")) return;

  link.addEventListener("click", (event) => {
    event.preventDefault();

    const previousScrollBehavior = document.documentElement.style.scrollBehavior;
    document.documentElement.style.scrollBehavior = "auto";
    target.scrollIntoView({ block: "start" });
    setActivePanel(target.id);

    if (window.location.hash !== `#${target.id}`) {
      window.history.pushState(null, "", `#${target.id}`);
    }

    window.requestAnimationFrame(() => {
      document.documentElement.style.scrollBehavior = previousScrollBehavior;
    });
  });
});

if (panels.length && panelLinks.length && "IntersectionObserver" in window) {
  const panelObserver = new IntersectionObserver(
    (entries) => {
      const visiblePanel = entries
        .filter((entry) => entry.isIntersecting)
        .sort((first, second) => second.intersectionRatio - first.intersectionRatio)[0];

      if (visiblePanel?.target.id) {
        setActivePanel(visiblePanel.target.id);
      }
    },
    { rootMargin: "-30% 0px -55%", threshold: [0, 0.1, 0.25, 0.5] },
  );

  panels.forEach((panel) => panelObserver.observe(panel));
}
