<script setup>
import { useToast } from '@/stores/toast';

const toast = useToast();
</script>

<template>
  <div class="ind-toast-host">
    <div v-for="t in toast.items" :key="t.id" class="ind-toast" :class="`ind-toast--${t.type}`">
      <b v-if="t.type === 'error'" class="c-stop">! </b>{{ t.msg }}
    </div>
  </div>
</template>
