import { OrderEditor } from "@/features/orders/components/order-editor";

/**
 * Edit a DRAFT (frontend-architecture §Structure). The id is a Next 16 route
 * param, still a promise in a server component; the editor fetches and seeds
 * itself, and only a DRAFT reaches here from the list.
 */
export default async function EditOrderPage({ params }: PageProps<"/orders/[id]/edit">) {
  const { id } = await params;
  return <OrderEditor orderId={Number(id)} />;
}
