import { defineComponent, h } from "vue";
import type { Component } from "vue";
import { ElIcon } from "element-plus";
import {
  Grid,
  MapLocation,
  Tickets,
  SwitchButton,
  ArrowRight,
  Lock,
  Menu,
  Close,
  Connection,
  Plus,
  Refresh,
  Search,
  Edit,
  Delete,
  DataAnalysis,
  DocumentCopy,
  Coin,
  CircleCheck,
  Warning,
  Clock,
  Loading,
} from "@element-plus/icons-vue";
function icon(component: Component) {
  return defineComponent({
    setup: () => () => h(ElIcon, null, () => h(component)),
  });
}
export const GridIcon = icon(Grid),
  GlobeIcon = icon(MapLocation),
  AuditIcon = icon(Tickets),
  LogoutIcon = icon(SwitchButton),
  ArrowRightIcon = icon(ArrowRight),
  LockIcon = icon(Lock),
  MenuIcon = icon(Menu),
  CloseIcon = icon(Close),
  ConnectionIcon = icon(Connection),
  PlusIcon = icon(Plus),
  RefreshIcon = icon(Refresh),
  SearchIcon = icon(Search),
  EditIcon = icon(Edit),
  DeleteIcon = icon(Delete),
  ProbeIcon = icon(DataAnalysis),
  CopyIcon = icon(DocumentCopy),
  DatabaseIcon = icon(Coin),
  SuccessIcon = icon(CircleCheck),
  WarningIcon = icon(Warning),
  ClockIcon = icon(Clock),
  LoadingIcon = icon(Loading);
