<script setup lang="ts">
import { useEditor, EditorContent } from '@tiptap/vue-3';
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import TextAlign from '@tiptap/extension-text-align';
import { watch, onBeforeUnmount } from 'vue';

const props = defineProps<{ modelValue?: string }>();
const emit = defineEmits(['update:modelValue']);

const editor = useEditor({
  content: props.modelValue || '',
  extensions: [
    StarterKit,
    Underline,
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
  ],
  onUpdate: ({ editor }) => {
    emit('update:modelValue', editor.getHTML());
  },
});

watch(() => props.modelValue, (newValue) => {
  const nextContent = newValue || '';
  if (editor.value?.getHTML() === nextContent) return;
  editor.value?.commands.setContent(nextContent);
});

onBeforeUnmount(() => editor.value?.destroy());
</script>

<template>
  <div class="border border-slate-300 rounded-md shadow-sm overflow-hidden bg-white" v-if="editor">
    <div class="bg-slate-100 border-b border-slate-300 px-3 py-2 flex flex-wrap gap-3 items-center">
      
      <NButtonGroup size="small">
        <NTooltip trigger="hover"><template #trigger>
          <NButton :type="editor.isActive('bold') ? 'primary' : 'default'" @click="editor.chain().focus().toggleBold().run()">
            <strong class="font-serif px-1">B</strong>
          </NButton>
        </template>加粗</NTooltip>
        
        <NTooltip trigger="hover"><template #trigger>
          <NButton :type="editor.isActive('italic') ? 'primary' : 'default'" @click="editor.chain().focus().toggleItalic().run()">
            <em class="font-serif px-1">I</em>
          </NButton>
        </template>斜体</NTooltip>

        <NTooltip trigger="hover"><template #trigger>
          <NButton :type="editor.isActive('underline') ? 'primary' : 'default'" @click="editor.chain().focus().toggleUnderline().run()">
            <u class="font-serif px-1">U</u>
          </NButton>
        </template>下划线</NTooltip>
      </NButtonGroup>

      <NButtonGroup size="small">
        <NTooltip trigger="hover"><template #trigger>
          <NButton :type="editor.isActive('heading', { level: 2 }) ? 'primary' : 'default'" @click="editor.chain().focus().toggleHeading({ level: 2 }).run()">
            H2 标题
          </NButton>
        </template>二级标题</NTooltip>
      </NButtonGroup>

      <NButtonGroup size="small">
        <NTooltip trigger="hover"><template #trigger>
          <NButton :type="editor.isActive('bulletList') ? 'primary' : 'default'" @click="editor.chain().focus().toggleBulletList().run()">
            • 列表
          </NButton>
        </template>无序列表</NTooltip>
        
        <NTooltip trigger="hover"><template #trigger>
          <NButton :type="editor.isActive('orderedList') ? 'primary' : 'default'" @click="editor.chain().focus().toggleOrderedList().run()">
            1. 列表
          </NButton>
        </template>有序列表</NTooltip>
      </NButtonGroup>
    </div>

    <div class="p-4 min-h-[150px] max-h-[400px] overflow-y-auto tiptap-content bg-white cursor-text" @click="editor?.commands.focus()">
      <EditorContent :editor="editor" />
    </div>
  </div>
</template>

<style>
.tiptap-content .ProseMirror { min-height: 120px; outline: none; }
.tiptap-content ul { list-style-type: disc; padding-left: 1.5rem; }
.tiptap-content ol { list-style-type: decimal; padding-left: 1.5rem; }
.tiptap-content h2 { font-size: 1.15rem; font-weight: 700; margin-top: 0.5rem; }
.tiptap-content p { margin-bottom: 0.25rem; }
</style>