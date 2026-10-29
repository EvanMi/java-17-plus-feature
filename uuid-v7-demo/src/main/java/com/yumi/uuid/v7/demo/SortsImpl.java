package com.yumi.uuid.v7.demo;

import java.util.Arrays;

public class SortsImpl {

    public static void main(String[] args) {
        int[] arr = {3, 2, 1, 5, 6, 20, 10};

        myBubbleSort(arr);
        System.out.println("冒泡排序: " + Arrays.toString(arr));

        int[] arr2 = {3, 2, 1, 5, 6, 20, 10};
        myInsertSort(arr2);
        System.out.println("插入排序: " + Arrays.toString(arr2));

        int[] arr3 = {3, 2, 1, 5, 6, 20, 10};
        mySelectSort(arr3);
        System.out.println("选择排序: " + Arrays.toString(arr3));

        int[] arr4 = {3, 2, 1, 5, 6, 20, 10};
        myQuickSort(arr4);
        System.out.println("快速排序: " + Arrays.toString(arr4));
    }

    private static void swap(int[] arr, int a, int b) {
        int tmp = arr[a];
        arr[a] = arr[b];
        arr[b] = tmp;
    }

    private static void myBubbleSort(int[] arr) {
        for (int i = 0; i < arr.length - 1; i++) {
            for (int j = 0; j < arr.length - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int tem = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tem;
                }
            }
        }
    }

    private static void myInsertSort(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            int key = arr[i];
            int j = i - 1;

            while (j >= 0 && arr[j] > key) {
                arr[j + 1] = arr[j];
                j--;
            }

            arr[j + 1] = key;
        }
    }

    private static void mySelectSort(int[] arr) {
        for (int i = 0; i < arr.length - 1; i++) {
            int minIndex = i;
            for (int j = i + 1; j < arr.length; j++) {
                if (arr[j] < arr[minIndex]) {
                    minIndex = j;
                }
            }

            if (minIndex != i) {
                int tem = arr[minIndex];
                arr[minIndex] = arr[i];
                arr[i] = tem;
            }
        }
    }

    // 快速排序：选取基准值，将数组划分为小于和大于基准的两部分，再递归排序
    private static void myQuickSort(int[] arr) {
        quickSort(arr, 0, arr.length - 1);
    }

    private static void quickSort(int[] arr, int left, int right) {
        if (left >= right) {
            return;
        }

        int pivotIndex = partition(arr, left, right);
        quickSort(arr, left, pivotIndex - 1);
        quickSort(arr, pivotIndex + 1, right);
    }

    // 分区：以最右侧元素为基准，小于基准的放左边，返回基准最终位置
    private static int partition(int[] arr, int left, int right) {
        int pivot = arr[right];
        int i = left; // i 指向第一个大于等于基准的位置

        for (int j = left; j < right; j++) {
            if (arr[j] < pivot) {
                swap(arr, i, j);
                i++;
            }
        }

        swap(arr, i, right);
        return i;
    }
}