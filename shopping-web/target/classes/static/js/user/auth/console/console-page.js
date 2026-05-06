(function () {
  const ME_PATH = "/shopping/user/auth/me";
  const PROFILE_PATH = "/shopping/user/profile";
  const authClient = window.ShoppingAuthClient;
  const avatarLink = document.getElementById("console-avatar-link");
  const avatarImage = document.getElementById("console-avatar-image");
  const avatarFallback = document.getElementById("console-avatar-fallback");
  let currentAvatarUrl = "";

  function normalizeAvatarUrl(value) {
    const normalized = value === null || value === undefined ? "" : String(value).trim();
    return /^https?:\/\//i.test(normalized) ? normalized : "";
  }

  function renderAvatar(user) {
    currentAvatarUrl = normalizeAvatarUrl(user?.avatarUrl);

    if (!currentAvatarUrl) {
      avatarImage.hidden = true;
      avatarImage.removeAttribute("src");
      avatarFallback.hidden = false;
      return;
    }

    avatarImage.hidden = true;
    avatarFallback.hidden = false;
    avatarImage.src = currentAvatarUrl;
  }

  avatarImage?.addEventListener("load", () => {
    if (!currentAvatarUrl) {
      return;
    }
    avatarImage.hidden = false;
    avatarFallback.hidden = true;
  });

  avatarImage?.addEventListener("error", () => {
    avatarImage.hidden = true;
    avatarFallback.hidden = false;
  });

  avatarLink?.addEventListener("click", (event) => {
    event.preventDefault();
    window.location.assign(PROFILE_PATH);
  });

  async function loadUser() {
    if (!authClient?.fetchWithAuth) {
      window.location.assign("/shopping/user/log-in");
      return;
    }

    try {
      const response = await authClient.fetchWithAuth(ME_PATH, { method: "GET" });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success || !payload?.user) {
        return;
      }
      renderAvatar(payload.user);
    } catch (_) {
      // Keep the page blank except for the fallback avatar.
    }
  }

  loadUser();
})();
