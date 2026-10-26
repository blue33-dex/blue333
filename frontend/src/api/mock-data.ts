/**
 * Mock Data Service · 前端独立运行的数据源
 *
 * 职责：提供符合 GLOSSARY 字段命名的模拟数据；模拟 Order/MaterialTask 状态流转；
 *      使用 localStorage 持久化，HMR / 刷新后数据不丢失；
 *      根据当前登录角色过滤 getMyOrders / getMyTasks；
 *      后端就绪后替换为真实 API 调用。
 *
 * 数据模型严格对齐 GLOSSARY §01 实体命名、§02 枚举值、§03 API 路径。
 */
import { OrderStatus, MaterialTaskStatus, MaterialType, Priority, RoleType } from '../types/enums'

const STORAGE_KEY = 'mock-data-v1'

// ========== 类型（Mock 专用，对齐 GLOSSARY 字段）==========
export interface MockOrder {
  id: number
  order_no: string
  customer_name: string
  use_scene: string
  delivery_deadline: string
  status: OrderStatus
  remark?: string
  am_id: number
  am_name: string
  material_specs: { id: number; order_id: number; material_type: MaterialType; spec_size: string }[]
  attachments: { id: number; order_id: number; file_name: string; file_size: number }[]
  reject_reason?: { reject_reason_type: string; remark: string; rejected_by: string }
  created_at: string
  updated_at: string
}

export interface MockMaterialTask {
  id: number
  order_id: number
  order_no: string
  material_spec_id: number
  material_type: MaterialType
  spec_size: string
  assignee_id?: number
  assignee_name?: string
  task_deadline: string
  priority: Priority
  status: MaterialTaskStatus
  created_at: string
  updated_at: string
}

// ========== 持久化工具 ===========
interface MockStorage {
  orders: MockOrder[]
  tasks: MockMaterialTask[]
  nextOrderId: number
  nextTaskId: number
}

function save(): void {
  const payload: MockStorage = { orders, tasks, nextOrderId, nextTaskId }
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(payload)) } catch { /* 忽略 */ }
}

function loadSeed(): MockStorage {
  return {
    nextOrderId: 2,
    nextTaskId: 101,
    orders: [
      {
        id: 1,
        order_no: 'ORD-20261020-001',
        customer_name: '上海睿思科技有限公司',
        use_scene: '产品发布会主视觉海报',
        delivery_deadline: '2026-11-15',
        status: OrderStatus.PENDING_CONFIRM,
        remark: '品牌色 Panton 286 C',
        am_id: 1,
        am_name: '张晓明',
        material_specs: [
          { id: 1, order_id: 1, material_type: MaterialType.POSTER, spec_size: '600mm × 900mm' },
        ],
        attachments: [
          { id: 1, order_id: 1, file_name: 'reference-poster.zip', file_size: 52428800 },
        ],
        created_at: '2026-10-20T10:30:00Z',
        updated_at: '2026-10-20T10:30:00Z',
      },
    ],
    tasks: [
      {
        id: 100,
        order_id: 1,
        order_no: 'ORD-20261015-002',
        material_spec_id: 5,
        material_type: MaterialType.POSTER,
        spec_size: '600mm × 900mm',
        assignee_id: 3,
        assignee_name: '李设计',
        task_deadline: '2026-10-28T18:00:00Z',
        priority: Priority.HIGH,
        status: MaterialTaskStatus.PENDING_APPROVAL,
        created_at: '2026-10-15T14:00:00Z',
        updated_at: '2026-10-20T09:00:00Z',
      },
    ],
  }
}

// ========== Mock 数据库（从 localStorage 恢复，否则用种子数据）==========
let orders: MockOrder[]
let tasks: MockMaterialTask[]
let nextOrderId: number
let nextTaskId: number

try {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (raw) {
    const parsed = JSON.parse(raw) as MockStorage
    orders = parsed.orders || []
    tasks = parsed.tasks || []
    // ID 计数器兜底：不仅处理"字段缺失"，也处理"计数器小于已有最大 ID"（数据被手工改写 / 旧版本残留）
    const maxOrderId = orders.reduce((m, o) => Math.max(m, Number(o.id) || 0), 0)
    const maxTaskId = tasks.reduce((m, t) => Math.max(m, Number(t.id) || 0), 0)
    nextOrderId = Math.max(Number(parsed.nextOrderId) || 0, maxOrderId + 1, 2)
    nextTaskId = Math.max(Number(parsed.nextTaskId) || 0, maxTaskId + 1, 101)
  } else {
    const seed = loadSeed()
    orders = seed.orders
    tasks = seed.tasks
    nextOrderId = seed.nextOrderId
    nextTaskId = seed.nextTaskId
    save()
  }
} catch {
  const seed = loadSeed()
  orders = seed.orders
  tasks = seed.tasks
  nextOrderId = seed.nextOrderId
  nextTaskId = seed.nextTaskId
}

// ========== 当前登录用户（Mock 层数据可见性 BR-13 依据）==========
/**
 * 解析当前登录用户，供 Mock 模拟 BR-13 数据可见性：
 *   - 优先读取 auth store 持久化的 current_user（含 id + roles）
 *   - 退化时解析 mock token 形态 mock-jwt-${role}-${timestamp}
 * 注意：这只是前端 Mock 的模拟隔离，真实隔离必须由后端 Repository 层按 BR-13 强制注入。
 */
function resolveCurrentUser(): { id: number; roles: RoleType[] } | null {
  try {
    const raw = localStorage.getItem('current_user')
    if (raw) {
      const u = JSON.parse(raw) as { id?: number; roles?: RoleType[] }
      if (u?.id && Array.isArray(u.roles) && u.roles.length) {
        return { id: Number(u.id), roles: u.roles }
      }
    }
  } catch {
    // 忽略解析失败，继续走 token 兜底
  }
  const token = localStorage.getItem('access_token') || ''
  const parts = token.split('-') // mock-jwt-${role}-${timestamp}
  if (parts.length >= 3) {
    const role = parts[2] as RoleType
    if (role === RoleType.AM) return { id: 1, roles: [role] }
    if (role === RoleType.BO) return { id: 2, roles: [role] }
    if (role === RoleType.DS) return { id: 3, roles: [role] }
  }
  return null
}

// ========== 业务方法 ===========
export const mockService = {
  /** 重置所有 mock 数据到种子状态（E2E 或手动调试用）*/
  reset(): void {
    const seed = loadSeed()
    orders = seed.orders
    tasks = seed.tasks
    nextOrderId = seed.nextOrderId
    nextTaskId = seed.nextTaskId
    save()
  },

  mockLogin(data: { username: string; password: string }) {
    // id 与角色对齐：AM=1 / BO=2 / DS=3（DS id 需与种子任务 assignee_id 一致，BR-13 过滤才有意义）
    const roleMap: Record<string, { id: number; role: string; name: string }> = {
      am_demo: { id: 1, role: RoleType.AM, name: '张晓明' },
      bo_demo: { id: 2, role: RoleType.BO, name: '王审核' },
      ds_demo: { id: 3, role: RoleType.DS, name: '李设计' },
    }
    const u = roleMap[data.username]
    if (!u || data.password !== 'demo123') {
      return { error: 'INVALID_CREDENTIALS', message: '用户名或密码错误' }
    }
    return {
      access_token: `mock-jwt-${u.role}-${Date.now()}`,
      refresh_token: `mock-refresh-${u.role}`,
      user: { id: u.id, username: data.username, name: u.name, roles: [u.role as RoleType] },
    }
  },

  /** A-24 AM 我的订单（BR-13：AM 仅返回自己创建的订单；BO 需要全量队列）*/
  getMyOrders(): MockOrder[] {
    const u = resolveCurrentUser()
    const role = u?.roles?.[0]
    const scoped = role === RoleType.AM && u ? orders.filter((o) => o.am_id === u.id) : [...orders]
    return scoped.sort((a, b) => b.created_at.localeCompare(a.created_at))
  },

  getKanban() {
    const columns: Record<string, MockOrder[]> = {}
    for (const s of Object.values(OrderStatus)) columns[s] = []
    for (const o of orders) columns[o.status].push(o)
    return { columns }
  },

  getOrderDetail(id: string): MockOrder | undefined {
    const o = orders.find((x) => x.id === Number(id))
    if (!o) return undefined
    // BR-13 模拟隔离：AM 只能取到自己创建的订单；DS 只能取到与自己任务相关的订单；BO 全量
    const u = resolveCurrentUser()
    const role = u?.roles?.[0]
    if (role === RoleType.AM && u && o.am_id !== u.id) return undefined
    if (role === RoleType.DS && u && !tasks.some((t) => t.order_id === o.id && t.assignee_id === u.id)) {
      return undefined
    }
    return o
  },

  createOrder(data: {
    customer_name?: string
    use_scene?: string
    delivery_deadline?: string
    remark?: string
    material_specs?: { material_type?: string; spec_size?: string }[]
  }) {
    const id = nextOrderId++
    const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    // 用 nextOrderId 本身作为序号来源，避免依赖 orders.length（数组删改会导致序号冲突）
    const seq = String(id).padStart(3, '0')
    const order: MockOrder = {
      id,
      order_no: `ORD-${today}-${seq}`,
      customer_name: data.customer_name || '示例客户',
      use_scene: data.use_scene || '示例场景',
      delivery_deadline: data.delivery_deadline || '2026-12-31',
      status: OrderStatus.PENDING_CONFIRM,
      remark: data.remark || '',
      am_id: 1,
      am_name: '张晓明',
      material_specs: (data.material_specs || []).map((s, i) => ({
        id: 100 + i, order_id: id,
        material_type: (s.material_type as MaterialType) || MaterialType.POSTER,
        spec_size: s.spec_size || '标准尺寸',
      })),
      attachments: [],
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }
    orders.unshift(order)
    save()
    return { orderId: id, order_no: order.order_no, status: order.status }
  },

  /** A-03 提交订单（AM 提交 / BO 退回后的重新提交，状态机 PENDING_CONFIRM → PENDING_CONFIRM）*/
  submitOrder(id: string) {
    const o = orders.find((x) => x.id === Number(id))
    if (!o) return { error: 'ORDER_NOT_FOUND', message: '订单不存在' }
    // PRD §3.2 状态机：仅 PENDING_CONFIRM 可提交
    if (o.status !== OrderStatus.PENDING_CONFIRM) {
      return { error: 'INVALID_STATE_TRANSITION', message: `订单当前状态为 ${o.status}，不可提交` }
    }
    // 说明：Phase 1 无 DRAFT 状态，createOrder 已直接为 PENDING_CONFIRM；
    // 重新提交保留 reject_reason 作为退回留痕（PRD BO-02：退回原因需可追溯），
    // 基线版本号（RequirementBaseline.baseline_version）由后端 P2 实现，Mock 层不虚构。
    o.updated_at = new Date().toISOString()
    save()
    return { orderId: o.id, status: o.status }
  },

  /** A-05 BO 退回订单（BR-03：退回原因类型 + 补充说明均必填；仅 PENDING_CONFIRM 可退回）*/
  rejectOrder(id: string, payload?: { reject_reason_type?: string; remark?: string }) {
    const o = orders.find((x) => x.id === Number(id))
    if (!o) return { error: 'ORDER_NOT_FOUND', message: '订单不存在' }
    if (o.status !== OrderStatus.PENDING_CONFIRM) {
      return { error: 'INVALID_STATE_TRANSITION', message: `订单当前状态为 ${o.status}，不可退回` }
    }
    const reasonType = payload?.reject_reason_type
    const remark = (payload?.remark || '').trim()
    if (!reasonType || !remark) {
      return { error: 'VALIDATION_ERROR', message: '退回原因类型与退回说明均为必填（BR-03）' }
    }
    o.reject_reason = {
      reject_reason_type: reasonType,
      remark,
      rejected_by: '王审核',
    }
    // 状态保持 PENDING_CONFIRM（PRD 状态机：PENDING_CONFIRM → PENDING_CONFIRM），
    // 订单留在 AM 待办并展示退回原因，AM 补充后调用 A-03 重新提交
    o.updated_at = new Date().toISOString()
    save()
    return { orderId: o.id, status: o.status }
  },

  assignTask(id: string, data: {
    assignee_id?: number
    assignee_name?: string
    task_deadline?: string
    priority?: Priority
  }) {
    const o = orders.find((x) => x.id === Number(id))
    if (!o) return { error: 'ORDER_NOT_FOUND' }
    // 前置条件：只有 PENDING_CONFIRM 状态可派发；重复派发拒绝
    if (o.status !== OrderStatus.PENDING_CONFIRM) {
      return { error: 'INVALID_STATE_TRANSITION', message: `订单当前状态为 ${o.status}，不可重复派发` }
    }
    o.status = OrderStatus.IN_PRODUCTION
    // 为每个物料规格创建 mock task
    o.material_specs.forEach((spec) => {
      const tid = nextTaskId++
      tasks.push({
        id: tid,
        order_id: o.id,
        order_no: o.order_no,
        material_spec_id: spec.id,
        material_type: spec.material_type,
        spec_size: spec.spec_size,
        assignee_id: data.assignee_id ?? 3,
        assignee_name: data.assignee_name ?? '李设计',
        task_deadline: data.task_deadline || '2026-11-01T18:00:00Z',
        priority: data.priority || Priority.MEDIUM,
        status: MaterialTaskStatus.PENDING_DESIGN,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      })
    })
    save()
    return { orderId: o.id, status: o.status, taskCount: o.material_specs.length }
  },

  /** S-05 DS 我的任务（BR-13：DS 仅返回派发给自己的任务；BO 可全量）*/
  getMyTasks(): MockMaterialTask[] {
    const u = resolveCurrentUser()
    const role = u?.roles?.[0]
    const scoped = role === RoleType.DS && u ? tasks.filter((t) => t.assignee_id === u.id) : [...tasks]
    return scoped.sort((a, b) => a.task_deadline.localeCompare(b.task_deadline))
  },

  getTaskDetail(id: string): MockMaterialTask | undefined {
    const t = tasks.find((x) => x.id === Number(id))
    if (!t) return undefined
    // BR-13 模拟隔离：DS 只能取到派发给自己的任务
    const u = resolveCurrentUser()
    if (u?.roles?.[0] === RoleType.DS && t.assignee_id !== u.id) return undefined
    return t
  },
}
