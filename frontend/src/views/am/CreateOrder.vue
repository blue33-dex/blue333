<script setup lang="ts">
// P1-T27 · AM 登记订单表单
// TODO(P1-T26) 附件上传（file input + multipart）位置预留，当前 mock 阶段暂不实现
// TODO(P1-T27) 后端就绪后 orderApi.createOrder / submitOrder 自动切换到真实 POST
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { MaterialType } from '../../types/enums'
import { orderApi } from '../../api/modules/order'

const router = useRouter()

const materialTypes: { value: string; label: string }[] = Object.values(MaterialType).map((v) => ({
  value: v,
  label: materialTypeLabel(v),
}))

function materialTypeLabel(v: string): string {
  const map: Record<string, string> = {
    [MaterialType.POSTER]: '海报 POSTER',
    [MaterialType.FLYER]: '单页 FLYER',
    [MaterialType.BANNER]: '横幅 BANNER',
    [MaterialType.BROCHURE]: '画册 BROCHURE',
    [MaterialType.ROLLUP]: '展架 ROLLUP',
    [MaterialType.OTHER]: '其他 OTHER',
  }
  return map[v] || v
}

interface SpecRow {
  material_type: string
  spec_size: string
}

const form = reactive({
  customer_name: '',
  use_scene: '',
  delivery_deadline: '',
  remark: '',
  material_specs: [{ material_type: MaterialType.POSTER, spec_size: '' }] as SpecRow[],
})

const loading = ref(false)
const submitting = ref(false)
const errors = ref<Record<string, string>>({})
const specErrors = ref<Record<number, Record<string, string>>>({})

function addSpec() {
  form.material_specs.push({ material_type: MaterialType.POSTER, spec_size: '' })
}

function removeSpec(idx: number) {
  if (form.material_specs.length <= 1) return
  form.material_specs.splice(idx, 1)
  // 清理旧错误
  const next: Record<number, Record<string, string>> = {}
  for (const [k, v] of Object.entries(specErrors.value)) {
    const ki = Number(k)
    if (ki < idx) next[ki] = v
    else if (ki > idx) next[ki - 1] = v
  }
  specErrors.value = next
}

function validate(): boolean {
  errors.value = {}
  specErrors.value = {}
  let ok = true
  if (!form.customer_name.trim()) {
    errors.value.customer_name = '客户名称必填'
    ok = false
  }
  if (!form.use_scene.trim()) {
    errors.value.use_scene = '使用场景必填'
    ok = false
  }
  if (!form.delivery_deadline) {
    errors.value.delivery_deadline = '交付截止日期必填'
    ok = false
  }
  if (!form.material_specs || form.material_specs.length === 0) {
    errors.value.material_specs = '至少添加一行物料规格'
    ok = false
  }
  form.material_specs.forEach((s, i) => {
    const row: Record<string, string> = {}
    if (!s.material_type) row.material_type = '请选择物料类型'
    if (!s.spec_size.trim()) row.spec_size = '规格尺寸必填'
    if (Object.keys(row).length) {
      specErrors.value[i] = row
      ok = false
    }
  })
  return ok
}

async function handleSubmit() {
  if (!validate()) return
  loading.value = true
  submitting.value = true
  try {
    // TODO(P1-T26) 上传附件后将返回的 file_ids 一并写入 material_specs 或 attachments 字段
    const created = await orderApi.createOrder({
      customer_name: form.customer_name.trim(),
      use_scene: form.use_scene.trim(),
      delivery_deadline: form.delivery_deadline,
      remark: form.remark.trim() || undefined,
      material_specs: form.material_specs.map((s) => ({
        material_type: s.material_type,
        spec_size: s.spec_size.trim(),
      })),
    })
    const orderId = created.data?.orderId
    if (orderId) {
      // 登记成功后立即提交 → 进入 BO 审阅队列
      await orderApi.submitOrder(orderId)
    }
    router.push('/am/orders')
  } catch (e: any) {
    errors.value.submit = e?.message || '提交失败，请重试'
  } finally {
    loading.value = false
    submitting.value = false
  }
}

async function handleSaveDraft() {
  if (!validate()) return
  loading.value = true
  try {
    await orderApi.createOrder({
      customer_name: form.customer_name.trim(),
      use_scene: form.use_scene.trim(),
      delivery_deadline: form.delivery_deadline,
      remark: form.remark.trim() || undefined,
      material_specs: form.material_specs.map((s) => ({
        material_type: s.material_type,
        spec_size: s.spec_size.trim(),
      })),
    })
    router.push('/am/orders')
  } catch (e: any) {
    errors.value.submit = e?.message || '保存失败'
  } finally {
    loading.value = false
  }
}

const todayStr = computed(() => new Date().toISOString().slice(0, 10))
</script>

<template>
  <div class="page">
    <header class="page-header">
      <h1>创建订单</h1>
      <p class="desc">登记客户需求 → 提交后自动进入 BO 审阅队列</p>
    </header>

    <form class="card" @submit.prevent="handleSubmit" novalidate>
      <section class="section">
        <h2>需求基线</h2>
        <label class="field">
          <span>客户名称 <em>*</em></span>
          <input v-model="form.customer_name" type="text" placeholder="如：上海睿思科技有限公司" />
          <small v-if="errors.customer_name" class="err">{{ errors.customer_name }}</small>
        </label>

        <label class="field">
          <span>使用场景 <em>*</em></span>
          <textarea
            v-model="form.use_scene"
            rows="3"
            placeholder="请描述物料用途，如：2026 Q4 新品发布会主视觉"
          />
          <small v-if="errors.use_scene" class="err">{{ errors.use_scene }}</small>
        </label>

        <div class="row">
          <label class="field">
            <span>交付截止日期 <em>*</em></span>
            <input
              v-model="form.delivery_deadline"
              type="date"
              :min="todayStr"
            />
            <small v-if="errors.delivery_deadline" class="err">{{ errors.delivery_deadline }}</small>
          </label>

          <label class="field">
            <span>备注（可选）</span>
            <input v-model="form.remark" type="text" placeholder="品牌色、禁忌事项等补充说明" />
          </label>
        </div>
      </section>

      <section class="section">
        <h2>物料规格</h2>
        <p class="section-hint">至少 1 行，每行选择物料类型 + 填写规格尺寸。</p>
        <small v-if="errors.material_specs" class="err block">{{ errors.material_specs }}</small>

        <div v-for="(spec, i) in form.material_specs" :key="i" class="spec-row">
          <div class="spec-cell type-cell">
            <label>物料类型 <em>*</em></label>
            <select v-model="spec.material_type">
              <option v-for="t in materialTypes" :key="t.value" :value="t.value">
                {{ t.label }}
              </option>
            </select>
            <small v-if="specErrors[i]?.material_type" class="err">{{ specErrors[i].material_type }}</small>
          </div>
          <div class="spec-cell size-cell">
            <label>规格尺寸 <em>*</em></label>
            <input v-model="spec.spec_size" type="text" placeholder="如 600mm × 900mm" />
            <small v-if="specErrors[i]?.spec_size" class="err">{{ specErrors[i].spec_size }}</small>
          </div>
          <div class="spec-cell action-cell">
            <button
              v-if="form.material_specs.length > 1"
              type="button"
              class="btn-mini danger"
              @click="removeSpec(i)"
            >删除</button>
          </div>
        </div>
        <button type="button" class="btn-mini" @click="addSpec">+ 新增规格行</button>
      </section>

      <section class="section">
        <h2>附件 <span class="tag-todo">P1-T26 待实现</span></h2>
        <div class="attach-placeholder">
          <p>此处预留附件上传区域（Logo、参考图、品牌规范等）。</p>
          <p class="muted">Phase 1 mock 暂不上传文件。后端对接后，在这里接入 file input → multipart 上传 → 将 file_id 写入订单。</p>
        </div>
      </section>

      <p v-if="errors.submit" class="global-err">{{ errors.submit }}</p>

      <footer class="actions">
        <button type="button" class="btn ghost" :disabled="loading" @click="handleSaveDraft">
          保存草稿
        </button>
        <button type="submit" class="btn primary" :disabled="loading">
          {{ submitting ? '提交中…' : '提交并送审' }}
        </button>
      </footer>
    </form>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 960px; margin: 0 auto; }
.page-header h1 { margin: 0 0 4px; font-size: 20px; }
.page-header .desc { margin: 0 0 16px; color: #666; font-size: 13px; }

.card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  padding: 24px;
}
.section { margin-bottom: 24px; }
.section h2 { font-size: 15px; margin: 0 0 12px; padding-left: 8px; border-left: 3px solid #409eff; }
.section-hint { margin: -6px 0 12px; color: #999; font-size: 12px; }

.field { display: block; margin-bottom: 14px; font-size: 13px; color: #333; }
.field > span { display: block; margin-bottom: 6px; }
.field em { color: #f56c6c; font-style: normal; margin-left: 2px; }
.field input[type="text"], .field input[type="date"], .field textarea, .field select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-sizing: border-box;
  font-size: 14px;
  font-family: inherit;
}
.field textarea { resize: vertical; }

.row { display: flex; gap: 16px; }
.row .field { flex: 1; }

.spec-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
  padding: 12px;
  background: #fafbfc;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.spec-cell { flex: 1; min-width: 0; }
.spec-cell label { display: block; margin-bottom: 4px; font-size: 12px; color: #606266; }
.spec-cell label em { color: #f56c6c; font-style: normal; }
.spec-cell input, .spec-cell select {
  width: 100%; padding: 6px 10px;
  border: 1px solid #dcdfe6; border-radius: 4px; box-sizing: border-box; font-size: 13px;
}
.action-cell { flex: 0 0 auto; display: flex; align-items: flex-end; padding-bottom: 0; }
.action-cell button { margin-top: 18px; }

.btn-mini {
  background: #f0f2f5;
  border: 1px solid #dcdfe6;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: #606266;
}
.btn-mini:hover { background: #e6e8eb; }
.btn-mini.danger { color: #f56c6c; border-color: #fbc4c4; }
.btn-mini.danger:hover { background: #fef0f0; }

.attach-placeholder {
  background: #f7f9fc;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 16px;
  color: #606266;
  font-size: 13px;
}
.attach-placeholder .muted { color: #999; margin: 4px 0 0; }
.tag-todo {
  font-size: 11px; padding: 1px 6px; background: #fdf6ec; color: #e6a23c;
  border-radius: 3px; margin-left: 6px; border: 1px solid #faecd8;
}

.err { color: #f56c6c; font-size: 12px; display: block; margin-top: 4px; }
.err.block { margin-bottom: 12px; }
.global-err {
  background: #fef0f0; color: #f56c6c;
  padding: 8px 12px; border-radius: 4px; margin-bottom: 12px;
}

.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; padding-top: 16px; border-top: 1px solid #ebeef5; }
.btn {
  padding: 9px 18px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}
.btn.primary { background: #409eff; border-color: #409eff; color: #fff; }
.btn.primary:hover:not(:disabled) { background: #66b1ff; border-color: #66b1ff; }
.btn.ghost { background: #fff; color: #606266; }
.btn.ghost:hover:not(:disabled) { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
