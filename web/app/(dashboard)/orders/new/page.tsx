import { OrderEditor } from "@/features/orders/components/order-editor";

/**
 * Create a draft (frontend-architecture §Structure names this route). The
 * page stays thin; the editor is shared with the edit route.
 */
export default function NewOrderPage() {
  return <OrderEditor />;
}
