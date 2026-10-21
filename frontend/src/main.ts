import { createApp } from "vue";
import {
  ConfigProvider,
  Alert,
  Avatar,
  Badge,
  Button,
  Checkbox,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Menu,
  Popover,
  Pagination,
  Select,
  Skeleton,
  Spin,
  Table,
  Tag,
  Tooltip,
} from "ant-design-vue";
import "ant-design-vue/dist/reset.css";
import App from "./App.vue";
import "./style.css";
const app = createApp(App);
for (const component of [
  ConfigProvider,
  Alert,
  Avatar,
  Badge,
  Button,
  Checkbox,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Menu,
  Popover,
  Pagination,
  Select,
  Skeleton,
  Spin,
  Table,
  Tag,
  Tooltip,
])
  app.use(component);
app.mount("#app");
