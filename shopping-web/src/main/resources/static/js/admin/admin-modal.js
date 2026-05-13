(function (root) {
  const dom = root.AdminDom;
  const detailDrawer = document.querySelector(".admin-detail-drawer");
  const detailTitle = document.getElementById("admin-detail-title");
  const detailBody = document.getElementById("admin-detail-body");
  const detailClose = document.querySelector(".admin-detail-drawer .admin-detail-close");

  function openDetail(card) {
    if (!card || !detailDrawer) {
      return;
    }
    openDetailContent(
      card.dataset.detailTitle || "模块详情",
      card.dataset.detailBody || "当前模块仅做前端交互展示。"
    );
  }

  function openDetailContent(title, body) {
    if (!detailDrawer) {
      return;
    }
    dom.setText(detailTitle, title);
    dom.setText(detailBody, body);
    detailDrawer.classList.add("is-open");
    detailDrawer.setAttribute("aria-hidden", "false");
  }

  function closeDetail() {
    if (!detailDrawer) {
      return;
    }
    detailDrawer.classList.remove("is-open");
    detailDrawer.setAttribute("aria-hidden", "true");
  }

  detailClose?.addEventListener("click", () => {
    dom.playPress(detailClose);
    closeDetail();
  });

  document.addEventListener("click", (event) => {
    if (!detailDrawer?.classList.contains("is-open")) {
      return;
    }
    if (event.target.closest(".admin-detail-drawer") || event.target.closest(".admin-console-card")) {
      return;
    }
    closeDetail();
  });

  root.AdminModal = {
    openDetail,
    openDetailContent,
    closeDetail
  };
})(window);
