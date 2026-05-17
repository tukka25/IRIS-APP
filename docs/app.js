/**
 * iris Landing Page — Scroll Animations
 * Uses IntersectionObserver for fade-in reveal animations.
 * Respects prefers-reduced-motion.
 */

(function () {
  // Respect reduced motion preference
  const prefersReducedMotion = window.matchMedia(
    '(prefers-reduced-motion: reduce)'
  ).matches;

  if (prefersReducedMotion) {
    // Show all elements immediately, skip animation setup
    document.querySelectorAll('.reveal').forEach(function (el) {
      el.classList.add('visible');
    });
    return;
  }

  // Threshold: element must be 15% visible before triggering
  const observer = new IntersectionObserver(
    function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          // Once shown, no need to observe further
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.15 }
  );

  // Observe all .reveal elements
  document.querySelectorAll('.reveal').forEach(function (el) {
    observer.observe(el);
  });
})();