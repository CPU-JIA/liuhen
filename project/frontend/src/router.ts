import { createRouter, createWebHistory } from "vue-router";
import { TOKEN_KEY, currentUser } from "./api";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("./views/LoginView.vue"),
    },
    {
      path: "/password",
      name: "password",
      component: () => import("./views/PasswordView.vue"),
    },
    {
      path: "/",
      name: "courses",
      component: () => import("./views/CoursesView.vue"),
    },
    {
      path: "/courses/:courseId",
      name: "assignments",
      component: () => import("./views/AssignmentsView.vue"),
      props: true,
    },
    {
      path: "/assignments/:assignmentId/work",
      name: "editor",
      component: () => import("./views/EditorView.vue"),
      props: true,
    },
    {
      path: "/works/:workId/timeline",
      name: "timeline",
      component: () => import("./views/TimelineView.vue"),
      props: true,
    },
    {
      path: "/works/:workId/declaration",
      name: "declaration",
      component: () => import("./views/DeclarationView.vue"),
      props: true,
    },
  ],
});

/** 路由守卫：未登录去登录页；首次登录必须先改密（AC-ACC-04-2）。 */
router.beforeEach((to) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (to.name !== "login" && !token) return { name: "login" };
  const me = currentUser();
  if (me?.mustChangePassword && to.name !== "password" && to.name !== "login")
    return { name: "password" };
  return true;
});
