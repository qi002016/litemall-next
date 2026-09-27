// ESLint flat config —— litemall-next C 端 H5 商城 (Vue3.5 + Vite8 + Vant4)
//
// 分层说明：
//   1) js.configs.recommended                    —— ESLint 官方推荐（正确性规则）
//   2) pluginVue.configs['flat/recommended']     —— eslint-plugin-vue vue3 recommended（106 条非排版规则全部保留）
//   3) 下方 rules 块仅做两件事：
//        a. 给 vue/multi-word-component-names 增加 ignores（规则未关闭，仅豁免既有页面级组件）
//        b. 关闭 12 条纯排版（layout）规则，交由格式化工具负责（工程当前未引入 Prettier）
//
// 参考：https://eslint.vuejs.org/user-guide/#bundler
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

export default [
  // 构建产物与依赖目录不参与检查（dist/ 已被 .gitignore，本地仍需显式忽略）
  {
    ignores: ['dist/**', 'node_modules/**', '.vite/**'],
  },

  // ESLint 官方推荐规则
  js.configs.recommended,

  // eslint-plugin-vue flat/recommended（vue3-essential + strongly-recommended + recommended）
  ...pluginVue.configs['flat/recommended'],

  {
    files: ['**/*.{js,mjs,cjs,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
      },
    },
    rules: {
      // 多词组件名：规则保持开启，仅豁免既有的页面级组件（views 下按路由命名的单文件组件）。
      // 新增页面若命名为单文件词仍会报错；如需彻底满足该规则，可重命名为 XxxView.vue。
      'vue/multi-word-component-names': [
        'error',
        {
          ignores: [
            'Cart',
            'Category',
            'Coupons',
            'Footprint',
            'Home',
            'Login',
            'Profile',
            'Register',
            'Search',
            'Setting',
            'User',
          ],
        },
      ],

      // 以下 12 条为 eslint-plugin-vue 归类为 layout（纯排版/空白）的规则，
      // flat/recommended 会全部以 warn 打开；本项目未引入 Prettier，
      // 为避免 600+ 条格式化告警淹没真实问题，统一显式关闭。
      'vue/first-attribute-linebreak': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/html-closing-bracket-spacing': 'off',
      'vue/html-indent': 'off',
      'vue/html-quotes': 'off',
      'vue/html-self-closing': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/mustache-interpolation-spacing': 'off',
      'vue/no-multi-spaces': 'off',
      'vue/no-spaces-around-equal-signs-in-attribute': 'off',
      'vue/singleline-html-element-content-newline': 'off',
    },
  },

  // 工程配置文件：Node 环境全局变量
  {
    files: ['*.config.js'],
    languageOptions: {
      globals: {
        ...globals.node,
      },
    },
  },
]