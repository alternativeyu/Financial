import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import LoginView from "./views/LoginView.vue";
import OpeningAuditView from "./views/OpeningAuditView.vue";
import "./style.css";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/login" },
    { path: "/login", component: LoginView },
    { path: "/opening-audit", component: OpeningAuditView }
  ]
});

createApp(App).use(router).mount("#app");
