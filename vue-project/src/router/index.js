import { createRouter, createWebHistory } from 'vue-router'
import UserCenterView from '../views/UserCenterView.vue'
import ResourceCenterView from '../views/ResourceCenterView.vue'
import CommunityNavigationView from '../views/CommunityNavigationView.vue'
import RecommendationView from '../views/RecommendationView.vue'
import InteractionCenterView from '../views/InteractionCenterView.vue'
import AdminCenterView from '../views/AdminCenterView.vue'
import LoginView from '../views/LoginView.vue'
import CommunityParticipationView from '../views/CommunityParticipationView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/user-center',
      name: 'user-center',
      component: UserCenterView,
    },
    {
      path: '/resource-center',
      name: 'resource-center',
      component: ResourceCenterView,
    },
    {
      path: '/community-navigation',
      name: 'community-navigation',
      component: CommunityNavigationView,
    },
    {
      path: '/recommendation',
      name: 'recommendation',
      component: RecommendationView,
    },
    {
      path: '/interaction-center',
      name: 'interaction-center',
      component: InteractionCenterView,
    },
    {
      path: '/community-participation',
      name: 'community-participation',
      component: CommunityParticipationView,
    },
    {
      path: '/admin-center',
      name: 'admin-center',
      component: AdminCenterView,
    },
  ],
})

router.beforeEach((to, from) => {
  const userJson = localStorage.getItem('user-center-current-user')
  let user = null
  try {
    user = userJson ? JSON.parse(userJson) : null
  } catch (e) {
    user = null
  }

  const isAdmin = user?.role === 'ADMIN'

  // List of front-stage paths that admins should not access
  const frontStagePaths = [
    '/user-center',
    '/resource-center',
    '/community-navigation',
    '/recommendation',
    '/interaction-center',
    '/community-participation',
  ]

  if (isAdmin && frontStagePaths.includes(to.path)) {
    return '/admin-center'
  }
})

export default router
