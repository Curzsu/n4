import request from './request'
import type { Product } from './types'

export const productApi = {
  getAllProducts(): Promise<Product[]> {
    return request.get('/api/products')
  },
  getProductById(id: number): Promise<Product> {
    return request.get(`/api/products/${id}`)
  },
  createProduct(product: Product): Promise<Product> {
    return request.post('/api/products', product)
  },
  updateProduct(id: number, product: Product): Promise<string> {
    return request.put(`/api/products/${id}`, product)
  },
  deleteProduct(id: number): Promise<string> {
    return request.delete(`/api/products/${id}`)
  },
  getProductPoints(id: number): Promise<any[]> {
    return request.get(`/api/products/${id}/points`)
  },
}
