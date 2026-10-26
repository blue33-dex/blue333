/**
 * Mock Data Service · 前端独立运行的数据源
 *
 * 职责：提供符合 GLOSSARY 字段命名的模拟数据；模拟 Order/MaterialTask 状态流转；
 *      不持久化（刷新即重置），后端就绪后替换为真实 API 调用。
 *
 * 数据模型严格对齐 GLOSSARY §01 实体命名、§02 枚举值、§03 API 路径。
 */
import { OrderStatus, MaterialTaskStatus, MaterialType, Priority } from '../types/enums'

// ========== Mock 数据库（内存）==========
let nextOrderId = 2
let nextTaskId = 101

const orders: MockOrder[] = [
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
]

const tasks: MockMaterialTask[] = [
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
]

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

// ========== 业务方法 ===========
export const mockService = {
  mockLogin(data: { username: string; password: string }) {
    const roleMap: Record<string, { role: string; name: string }> = {
      am_demo: { role: 'AM', name: '张晓明' },
      bo_demo: { role: 'BO', name: '王审核' },
      ds_demo: { role: 'DS', name: '李设计' },
    }
    const u = roleMap[data.username]
    if (!u || data.password !== 'demo123') {
      // 返回错误不中断——由 axios 拦截器识别
      return { error: 'INVALID_CREDENTIALS', message: '用户名或密码错误' }
    }
    return {
      access_token: `mock-jwt-${u.role}-${Date.now()}`,
      refresh_token: `mock-refresh-${u.role}`,
      user: { id: 1, username: data.username, name: u.name, roles: [u.role] },
    }
  },

  getMyOrders(): MockOrder[] {
    // 模拟当前 AM 视角
    return [...orders].sort((a, b) => b.created_at.localeCompare(a.created_at))
  },

  getKanban() {
    const columns: Record<string, MockOrder[]> = {}
    for (const s of Object.values(OrderStatus)) columns[s] = []
    for (const o of orders) columns[o.status].push(o)
    return { columns }
  },

  getOrderDetail(id: string): MockOrder | undefined {
    return orders.find((o) => o.id === Number(id))
  },

  createOrder(data: any) {
    const id = nextOrderId++
    const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    const seq = String(orders.length + 1).padStart(3, '0')
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
      material_specs: (data.material_specs || []).map((s: any, i: number) => ({
        id: 100 + i, order_id: id,
        material_type: s.material_type || MaterialType.POSTER,
        spec_size: s.spec_size || '标准尺寸',
      })),
      attachments: [],
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }
    orders.unshift(order)
    return { orderId: id, order_no: order.order_no, status: order.status }
  },

  submitOrder(id: string) {
    const o = orders.find((x) => x.id === Number(id))
    // PRD Phase 1 无 DRAFT 状态。createOrder 已设 PENDING_CONFIRM（BO 可审阅）；
    // submitOrder 的业务含义是"AM 主动将订单推入 BO 审阅队列"，当前状态已是就绪态，
    // 所以此处仅记录 updated_at 时间戳，后端接入时应补审计日志。
    if (o) {
      o.updated_at = new Date().toISOString()
    }
  },

  rejectOrder(id: string, payload?: { reject_reason_type?: string; remark?: string }) {
    const o = orders.find((x) => x.id === Number(id))
    if (o && o.status === OrderStatus.PENDING_CONFIRM) {
      o.reject_reason = {
        reject_reason_type: payload?.reject_reason_type || 'INFORMATION_INCOMPLETE',
        remark: payload?.remark || '(未填写退回说明)',
        rejected_by: '王审核',
      }
      // TODO: 后端就绪后，退回状态转移（如改为 REJECTED 子状态）由状态机强制；
      // Phase 1 暂保持 PENDING_CONFIRM，退回后 AM 可补充信息再次 submit
    }
  },

  assignTask(id: string, data: { assignee_id?: number; assignee_name?: string; task_deadline?: string; priority?: Priority }) {
    const o = orders.find((x) => x.id === Number(id))
    if (!o) return { error: 'ORDER_NOT_FOUND' }
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
    return { orderId: o.id, status: o.status, taskCount: o.material_specs.length }
  },

  getMyTasks(): MockMaterialTask[] {
    return [...tasks].sort((a, b) => a.task_deadline.localeCompare(b.task_deadline))
  },

  getTaskDetail(id: string): MockMaterialTask | undefined {
    return tasks.find((t) => t.id === Number(id))
  },
}
