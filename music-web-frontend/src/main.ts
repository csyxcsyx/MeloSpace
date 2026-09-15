import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
import "./styles/main.css";
import { installInputModalityTracking } from "./utils/inputModality";

const app = createApp(App);

installInputModalityTracking();
app.use(createPinia());
app.use(router);
app.mount("#app");
