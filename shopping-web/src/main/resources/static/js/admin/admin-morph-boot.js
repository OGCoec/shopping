(function () {
  var key = "shopping:admin:parti-morph:pending:v1";
  try {
    if (sessionStorage.getItem(key) === "1") {
      document.documentElement.classList.add("admin-morph-pending");
      window.setTimeout(function () {
        document.documentElement.classList.remove("admin-morph-pending");
        try {
          sessionStorage.removeItem(key);
        } catch (_) {
        }
      }, 6000);
    }
  } catch (_) {
  }
})();
